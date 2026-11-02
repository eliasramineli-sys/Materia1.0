package br.com.materia;

import br.com.materia.domain.*;
import br.com.materia.infra.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CsvAndNumbersTest {
    @TempDir Path temp;
    @ParameterizedTest @ValueSource(strings={"NaN","inf","Infinity","-1","1e3","1.234,50","1,234.50","1,0000","","1 000","999999999999999999999999"})
    void rejectsInvalidQuantity(String value){assertThrows(IllegalArgumentException.class,()->Numbers.quantity(value));}
    @Test void numericPrecisionAndLocaleAreExact(){assertEquals(123456,Numbers.quantity("123,456"));assertEquals(123456,Numbers.quantity("123.456"));assertEquals(1050,Numbers.money("10,50"));assertEquals("0,3",Numbers.qty(300));assertThrows(IllegalArgumentException.class,()->Numbers.money("1,234"));}
    @Test void weightedCostAvoidsLongMultiplicationOverflow(){assertEquals(99999999999L,Numbers.weightedCost(500000000000L,99999999999L,100000000000L,99999999999L));}
    @Test void csvHandlesBomQuotesSemicolonAndEmbeddedNewline() throws Exception {
        Path f=temp.resolve("table.csv");List<List<String>> input=List.of(List.of("Código","Observação"),List.of("001","Ele disse \"sim\";\nlinha 2"));Csv.write(f,input.get(0),input.subList(1,2));assertEquals(input,Csv.parse(Files.readString(f)));
    }
    @Test void csvProtectsSpreadsheetFormulaCells() throws Exception {Path f=temp.resolve("safe.csv");Csv.write(f,List.of("x"),List.of(List.of("=HYPERLINK(\"x\")"),List.of(" @soma")));List<List<String>> rows=Csv.parse(Files.readString(f));assertTrue(rows.get(1).get(0).startsWith("'="));assertTrue(rows.get(2).get(0).startsWith("'"));}
    @ParameterizedTest @ValueSource(strings={"a;\"não fecha","\"a\"x;b","a\"b;c"})
    void csvRejectsMalformedQuotes(String text){assertThrows(IllegalArgumentException.class,()->Csv.parse(text));}
    @Test void importTemplateProducesValidatedBatch() throws Exception {Path f=temp.resolve("model.csv");Csv.template(f);Csv.ImportBatch b=Csv.readImport(f);assertEquals(1,b.materials().size());assertEquals("EX-001",b.materials().get(0).sku());assertEquals(10000,b.quantities().get(0));}
    @Test void importRejectsDuplicateCodesBeforeWriting() throws Exception {Path f=temp.resolve("model.csv");Csv.template(f);String s=Files.readString(f);List<List<String>> rows=Csv.parse(s);Csv.write(f,Csv.HEADER,List.of(rows.get(1),rows.get(1)));assertThrows(IllegalArgumentException.class,()->Csv.readImport(f));}
    @Test void htmlEscapesUntrustedMaterialFields() throws Exception {Material m=new Material(1,"A","<script>alert(1)</script>","","UN","",null,"","",100,200,0,1000,"",true);Path out=temp.resolve("report.html");Reports.html(out,"A & B",List.of(m),List.of(),false);String html=Files.readString(out);assertFalse(html.contains("<script>"));assertTrue(html.contains("&lt;script&gt;"));assertTrue(html.contains("A &amp; B"));}
}
