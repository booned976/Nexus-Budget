package com.nexusbudget.assistant

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.nexusbudget.core.demo.DemoData
import com.nexusbudget.core.engine.FinancialPicture
import com.nexusbudget.core.model.Categories
import com.nexusbudget.core.model.CategoryIndex
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssistantTest {

    private val today = LocalDate.of(2026, 9, 18)
    private val demo = DemoData.generate(today)
    private val categories = CategoryIndex(Categories.defaults)
    private val picture = FinancialPicture.compute(today, demo.accounts, demo.transactions, categories, demo.budgets, demo.goals, demo.snapshots)
    private val context = AssistantContext(picture, demo.transactions, categories, demo.budgets, shareTransactionDetails = true)

    @Test
    fun `every read-only tool returns valid JSON`() {
        val inputs = mapOf(
            "search_transactions" to """{"merchant": "bistro", "limit": 5}""",
            "simulate_debt_payoff" to """{"strategy": "avalanche", "extra_monthly": 200}""",
            "debt_extra_needed" to """{"target_months": 36}""",
            "project_goal" to """{"target_amount": 5000, "monthly_contribution": 250}""",
            "get_spending_by_category" to """{"month": "2026-08"}""",
        )
        for (spec in FinanceTools.specs.filter { !it.name.startsWith("propose") }) {
            val outcome = FinanceTools.execute(spec.name, inputs[spec.name] ?: "{}", context)
            assertFalse(outcome.isError, "${spec.name}: ${outcome.content}")
            Json.parseToJsonElement(outcome.content)
        }
    }

    @Test
    fun `debt simulation reports savings`() {
        val outcome = FinanceTools.execute("simulate_debt_payoff", """{"strategy":"snowball","extra_monthly":150}""", context)
        val json = Json.parseToJsonElement(outcome.content).jsonObject
        assertEquals("Snowball", json["strategy"]!!.jsonPrimitive.content)
        assertTrue(json["interest_saved_vs_minimums_only"]!!.jsonPrimitive.content.toDouble() > 0)
        assertEquals("Store Card", json["payoff_order"]!!.jsonArray.first().jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `invalid input and privacy settings produce tool errors`() {
        assertTrue(FinanceTools.execute("simulate_debt_payoff", """{"strategy":"avalanche"}""", context).isError)
        assertTrue(FinanceTools.execute("simulate_debt_payoff", """{"strategy":"yolo","extra_monthly":1}""", context).isError)
        assertTrue(FinanceTools.execute("search_transactions", "{not json", context).isError)
        assertTrue(FinanceTools.execute("get_spending_by_category", """{"month":"September"}""", context).isError)
        val private = context.copy(shareTransactionDetails = false)
        assertTrue(FinanceTools.execute("search_transactions", "{}", private).isError)
        val recurring = FinanceTools.execute("get_recurring_charges", "{}", private)
        assertFalse(recurring.content.contains("Streamflix"))
        assertFalse(FinancialAssistant.tools(AssistantSettings("k", shareTransactionDetails = false)).any { it.name() == "search_transactions" })
    }

    @Test
    fun `proposals are validated`() {
        val ok = FinanceTools.execute(
            "propose_budget_changes",
            """{"changes":[{"category_id":"food.restaurants","monthly_amount":300}],"rationale":"More realistic"}""",
            context,
        )
        val proposal = assertIs<Proposal.BudgetChanges>(ok.proposal)
        assertEquals(300_00, proposal.changes.single().monthlyAmount)
        assertEquals(260_00, proposal.changes.single().previousAmount)
        assertTrue(FinanceTools.execute("propose_budget_changes", """{"changes":[{"category_id":"nope","monthly_amount":1}],"rationale":"x"}""", context).isError)
        assertTrue(FinanceTools.execute("propose_budget_changes", """{"changes":[{"category_id":"income.paycheck","monthly_amount":1}],"rationale":"x"}""", context).isError)
        val goal = FinanceTools.execute("propose_goal", """{"name":"Car fund","type":"purchase","target_amount":8000,"monthly_contribution":300,"rationale":"x"}""", context)
        assertIs<Proposal.NewGoal>(goal.proposal)
    }

    @Test
    fun `assistant runs tools and streams the final answer`() = runTest {
        val server = MockWebServer()
        server.enqueue(sse(TOOL_CALL_STREAM))
        server.enqueue(sse(FINAL_ANSWER_STREAM))
        server.start()
        try {
            val assistant = FinancialAssistant { key ->
                AnthropicOkHttpClient.builder().apiKey(key).baseUrl(server.url("/").toString().trimEnd('/')).maxRetries(0).build()
            }
            val deltas = StringBuilder()
            val statuses = mutableListOf<String>()
            val reply = assistant.send(
                "How fast can I be debt free?",
                AssistantSettings(apiKey = "test-key"),
                context,
                object : AssistantListener {
                    override fun onTextDelta(text: String) { deltas.append(text) }
                    override fun onToolStatus(label: String) { statuses += label }
                },
            )
            assertNull(reply.error, reply.text)
            assertEquals("Let me run the numbers.\n\nWith **\$200** extra you'd be debt-free sooner.", reply.text)
            assertEquals(listOf("Running a payoff plan"), statuses)
            assertTrue(deltas.contains("debt-free"))

            val first = server.takeRequest()
            assertEquals("/v1/messages", first.path)
            assertEquals("test-key", first.getHeader("x-api-key"))
            assertTrue(first.getHeader("anthropic-beta")!!.contains("server-side-fallback-2026-07-01"))
            val firstBody = Json.parseToJsonElement(first.body.readUtf8()).jsonObject
            assertEquals("claude-opus-5", firstBody["model"]!!.jsonPrimitive.content)
            assertEquals("default", firstBody["fallbacks"]!!.jsonPrimitive.content)
            assertEquals(true.toString(), firstBody["stream"]!!.jsonPrimitive.content)
            assertNotNull(firstBody["tools"])

            val second = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            val messages = second["messages"]!!.jsonArray
            assertEquals(3, messages.size)
            val toolResult = messages[2].jsonObject["content"]!!.jsonArray.first().jsonObject
            assertEquals("tool_result", toolResult["type"]!!.jsonPrimitive.content)
            assertEquals("toolu_1", toolResult["tool_use_id"]!!.jsonPrimitive.content)
            val resultJson = Json.parseToJsonElement(toolResult["content"]!!.jsonPrimitive.content) as JsonObject
            assertEquals("Avalanche", resultJson["strategy"]!!.jsonPrimitive.content)
            assertFalse(assistant.isEmpty)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `api errors roll back the turn`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""))
        server.start()
        try {
            val assistant = FinancialAssistant { key ->
                AnthropicOkHttpClient.builder().apiKey(key).baseUrl(server.url("/").toString().trimEnd('/')).maxRetries(0).build()
            }
            val reply = assistant.send("Hi", AssistantSettings(apiKey = "bad"), context, object : AssistantListener {})
            assertNotNull(reply.error)
            assertTrue(reply.text.contains("API key"))
            assertTrue(assistant.isEmpty)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `restored history alternates roles`() {
        val assistant = FinancialAssistant { throw IllegalStateException("not used") }
        assistant.restore(
            listOf(
                ChatTurn(ChatRole.ASSISTANT, "Hi! Ask me anything."),
                ChatTurn(ChatRole.USER, "Question 1"),
                ChatTurn(ChatRole.ASSISTANT, "Answer 1"),
                ChatTurn(ChatRole.USER, "Unanswered"),
            ),
        )
        assertFalse(assistant.isEmpty)
    }

    private fun sse(events: String) = MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setBody(events.trimIndent() + "\n\n")

    companion object {
        private val TOOL_CALL_STREAM = """
            event: message_start
            data: {"type":"message_start","message":{"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5","content":[],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":10,"output_tokens":1}}}

            event: content_block_start
            data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Let me run the numbers."}}

            event: content_block_stop
            data: {"type":"content_block_stop","index":0}

            event: content_block_start
            data: {"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"toolu_1","name":"simulate_debt_payoff","input":{}}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"strategy\": \"avalanche\", "}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"\"extra_monthly\": 200}"}}

            event: content_block_stop
            data: {"type":"content_block_stop","index":1}

            event: message_delta
            data: {"type":"message_delta","delta":{"stop_reason":"tool_use","stop_sequence":null},"usage":{"output_tokens":40}}

            event: message_stop
            data: {"type":"message_stop"}
        """

        private val FINAL_ANSWER_STREAM = """
            event: message_start
            data: {"type":"message_start","message":{"id":"msg_2","type":"message","role":"assistant","model":"claude-opus-5","content":[],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":50,"output_tokens":1}}}

            event: content_block_start
            data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"With **${'$'}200** extra you'd be "}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"debt-free sooner."}}

            event: content_block_stop
            data: {"type":"content_block_stop","index":0}

            event: message_delta
            data: {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":20}}

            event: message_stop
            data: {"type":"message_stop"}
        """
    }
}
