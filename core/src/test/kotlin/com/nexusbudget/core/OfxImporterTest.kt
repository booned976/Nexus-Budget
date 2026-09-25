package com.nexusbudget.core

import com.nexusbudget.core.importer.OfxImporter
import com.nexusbudget.core.importer.StatementKind
import com.nexusbudget.core.model.AccountType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfxImporterTest {

    private val sgmlBank = """
        OFXHEADER:100
        DATA:OFXSGML
        VERSION:102
        SECURITY:NONE
        ENCODING:USASCII
        CHARSET:1252
        COMPRESSION:NONE
        OLDFILEUID:NONE
        NEWFILEUID:NONE

        <OFX>
        <SIGNONMSGSRSV1><SONRS><STATUS><CODE>0<SEVERITY>INFO</STATUS><DTSERVER>20260920120000[-5:EST]<LANGUAGE>ENG
        <FI><ORG>Riverside Credit Union<FID>1234</FI></SONRS></SIGNONMSGSRSV1>
        <BANKMSGSRSV1><STMTTRNRS><TRNUID>1<STATUS><CODE>0<SEVERITY>INFO</STATUS>
        <STMTRS><CURDEF>USD
        <BANKACCTFROM><BANKID>071000013<ACCTID>000123454821<ACCTTYPE>CHECKING</BANKACCTFROM>
        <BANKTRANLIST><DTSTART>20260901<DTEND>20260920
        <STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20260915120000.000[-5:EST]<TRNAMT>-54.23<FITID>2026091501<NAME>GREENLEAF GROCERY #12<MEMO>POS PURCHASE</STMTTRN>
        <STMTTRN><TRNTYPE>CREDIT<DTPOSTED>20260915<TRNAMT>2150.00<FITID>2026091502<NAME>ACME CORP PAYROLL</STMTTRN>
        <STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20260916<TRNAMT>-12.50<FITID>2026091603<MEMO><NAME>JOE &amp; SONS CAFE</STMTTRN>
        </BANKTRANLIST>
        <LEDGERBAL><BALAMT>3184.27<DTASOF>20260920</LEDGERBAL>
        <AVAILBAL><BALAMT>3100.00<DTASOF>20260920</AVAILBAL>
        </STMTRS></STMTTRNRS>
        <STMTTRNRS><TRNUID>2<STATUS><CODE>0<SEVERITY>INFO</STATUS>
        <STMTRS><CURDEF>USD
        <BANKACCTFROM><BANKID>071000013<ACCTID>000123459134<ACCTTYPE>SAVINGS</BANKACCTFROM>
        <BANKTRANLIST><DTSTART>20260901<DTEND>20260920</BANKTRANLIST>
        <LEDGERBAL><BALAMT>6240.00<DTASOF>20260920</LEDGERBAL>
        </STMTRS></STMTTRNRS></BANKMSGSRSV1>
        </OFX>
    """.trimIndent()

    @Test
    fun `reads SGML bank statements with unclosed values`() {
        val statements = OfxImporter.parse(sgmlBank)
        assertEquals(2, statements.size)

        val checking = statements[0]
        assertEquals(StatementKind.BANK, checking.kind)
        assertEquals(AccountType.CHECKING, checking.suggestedType)
        assertEquals("Riverside Credit Union", checking.institution)
        assertEquals("4821", checking.mask)
        assertEquals(3184_27L, checking.balance)
        assertEquals(3100_00L, checking.available)
        assertEquals(LocalDate.of(2026, 9, 20), checking.balanceDate)
        assertEquals(3, checking.transactions.size)

        val grocery = checking.transactions[0]
        assertEquals("2026091501", grocery.id)
        assertEquals(LocalDate.of(2026, 9, 15), grocery.date)
        assertEquals(-54_23L, grocery.amount)
        assertEquals("GREENLEAF GROCERY #12", grocery.description)
        assertEquals(2150_00L, checking.transactions[1].amount)
        // An empty value with no closing tag must not swallow the values after it.
        assertEquals("JOE & SONS CAFE", checking.transactions[2].description)
        assertEquals(-12_50L, checking.transactions[2].amount)

        val savings = statements[1]
        assertEquals(AccountType.SAVINGS, savings.suggestedType)
        assertEquals(6240_00L, savings.balance)
        assertTrue(savings.transactions.isEmpty())
    }

    @Test
    fun `reads XML credit card statements and treats the balance as owed`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="no"?>
            <?OFX OFXHEADER="200" VERSION="220" SECURITY="NONE" OLDFILEUID="NONE" NEWFILEUID="NONE"?>
            <OFX>
              <SIGNONMSGSRSV1><SONRS><STATUS><CODE>0</CODE><SEVERITY>INFO</SEVERITY></STATUS>
                <FI><ORG>Demo Card Services</ORG><FID>99</FID></FI></SONRS></SIGNONMSGSRSV1>
              <CREDITCARDMSGSRSV1><CCSTMTTRNRS><TRNUID>0</TRNUID>
                <CCSTMTRS>
                  <CURDEF>USD</CURDEF>
                  <CCACCTFROM><ACCTID>4111111111117702</ACCTID></CCACCTFROM>
                  <BANKTRANLIST>
                    <STMTTRN><TRNTYPE>DEBIT</TRNTYPE><DTPOSTED>20260910</DTPOSTED><TRNAMT>-89.99</TRNAMT><FITID>A1</FITID><NAME>STREAMFLIX</NAME><MEMO/></STMTTRN>
                    <STMTTRN><TRNTYPE>CREDIT</TRNTYPE><DTPOSTED>20260912</DTPOSTED><TRNAMT>500.00</TRNAMT><FITID>A2</FITID><NAME>PAYMENT THANK YOU</NAME></STMTTRN>
                  </BANKTRANLIST>
                  <LEDGERBAL><BALAMT>-2386.45</BALAMT><DTASOF>20260920</DTASOF></LEDGERBAL>
                </CCSTMTRS>
              </CCSTMTTRNRS></CREDITCARDMSGSRSV1>
            </OFX>
        """.trimIndent()
        val card = OfxImporter.parse(xml).single()
        assertEquals(StatementKind.CREDIT_CARD, card.kind)
        assertEquals(AccountType.CREDIT_CARD, card.suggestedType)
        assertEquals("7702", card.mask)
        assertEquals(2386_45L, card.balance)
        assertNull(card.available)
        assertEquals(listOf(-89_99L, 500_00L), card.transactions.map { it.amount })
        assertEquals("STREAMFLIX", card.transactions[0].description)
    }

    @Test
    fun `reads brokerage positions, cash and only cash transfers`() {
        val investment = """
            OFXHEADER:100
            DATA:OFXSGML
            VERSION:102

            <OFX>
            <SIGNONMSGSRSV1><SONRS><STATUS><CODE>0<SEVERITY>INFO</STATUS><DTSERVER>20260920</SONRS></SIGNONMSGSRSV1>
            <INVSTMTMSGSRSV1><INVSTMTTRNRS><TRNUID>1<STATUS><CODE>0<SEVERITY>INFO</STATUS>
            <INVSTMTRS><DTASOF>20260919<CURDEF>USD
            <INVACCTFROM><BROKERID>demo-investing.example<ACCTID>55553310</INVACCTFROM>
            <INVTRANLIST><DTSTART>20260801<DTEND>20260919
            <BUYSTOCK><INVBUY><INVTRAN><FITID>B1<DTTRADE>20260805</INVTRAN><SECID><UNIQUEID>111111111<UNIQUEIDTYPE>CUSIP</SECID><UNITS>5<UNITPRICE>100<TOTAL>-500</INVBUY><BUYTYPE>BUY</BUYSTOCK>
            <INVBANKTRAN><STMTTRN><TRNTYPE>CREDIT<DTPOSTED>20260801<TRNAMT>1000.00<FITID>T1<NAME>ACH DEPOSIT</STMTTRN><SUBACCTFUND>CASH</INVBANKTRAN>
            </INVTRANLIST>
            <INVPOSLIST>
            <POSSTOCK><INVPOS><SECID><UNIQUEID>111111111<UNIQUEIDTYPE>CUSIP</SECID><HELDINACCT>CASH<POSTYPE>LONG<UNITS>40<UNITPRICE>250.50<MKTVAL>10020.00<DTPRICEASOF>20260919</INVPOS></POSSTOCK>
            <POSMF><INVPOS><SECID><UNIQUEID>222222222<UNIQUEIDTYPE>CUSIP</SECID><HELDINACCT>CASH<POSTYPE>LONG<UNITS>100.5<UNITPRICE>80<MKTVAL>8040.00<DTPRICEASOF>20260919</INVPOS></POSMF>
            </INVPOSLIST>
            <INVBAL><AVAILCASH>392.91<MARGINBALANCE>0<SHORTBALANCE>0</INVBAL>
            </INVSTMTRS></INVSTMTTRNRS></INVSTMTMSGSRSV1>
            <SECLISTMSGSRSV1><SECLIST>
            <STOCKINFO><SECINFO><SECID><UNIQUEID>111111111<UNIQUEIDTYPE>CUSIP</SECID><SECNAME>Example Tech Inc<TICKER>EXT</SECINFO></STOCKINFO>
            <MFINFO><SECINFO><SECID><UNIQUEID>222222222<UNIQUEIDTYPE>CUSIP</SECID><SECNAME>Total Market Index Fund<TICKER>TMIF</SECINFO></MFINFO>
            </SECLIST></SECLISTMSGSRSV1>
            </OFX>
        """.trimIndent()
        val brokerage = OfxImporter.parse(investment).single()
        assertEquals(StatementKind.INVESTMENT, brokerage.kind)
        assertEquals(AccountType.BROKERAGE, brokerage.suggestedType)
        assertEquals("demo-investing.example", brokerage.institution)
        assertEquals(10020_00L + 8040_00 + 392_91, brokerage.balance)
        assertEquals(LocalDate.of(2026, 9, 19), brokerage.balanceDate)
        assertEquals(listOf("Example Tech Inc", "Total Market Index Fund", "Cash"), brokerage.holdings.map { it.name })
        assertEquals("EXT", brokerage.holdings[0].ticker)
        assertEquals(100.5, brokerage.holdings[1].quantity)
        // The buy stays inside the account; only the deposit counts as money moving.
        assertEquals(listOf(1000_00L), brokerage.transactions.map { it.amount })
    }

    @Test
    fun `account key is stable and never contains the account number`() {
        val first = OfxImporter.parse(sgmlBank)[0]
        val again = OfxImporter.parse(sgmlBank)[0]
        assertEquals(first.accountKey, again.accountKey)
        assertFalse(first.accountKey.contains("4821"))
        assertTrue(first.accountKey != OfxImporter.parse(sgmlBank)[1].accountKey)
    }

    @Test
    fun `recognizes OFX files and ignores CSV`() {
        assertTrue(OfxImporter.looksLikeOfx(sgmlBank))
        assertFalse(OfxImporter.looksLikeOfx("Date,Description,Amount\n2026-09-01,Coffee,-4.50"))
        assertTrue(OfxImporter.parse("Date,Description,Amount").isEmpty())
    }
}
