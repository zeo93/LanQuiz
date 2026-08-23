package com.marco.lanquiz;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/** Prove sul lettore dei banchi: formato storico, estensioni e file veri. */
public class ParserTest {

    @Test
    public void formatoStorico_primaRispostaEsatta() {
        List<Question> qs = Parser.parse("Capitale d'Italia?;Roma;Milano;Torino");
        assertEquals(1, qs.size());
        Question q = qs.get(0);
        assertEquals("Capitale d'Italia?", q.text);
        assertEquals(3, q.answers.size());
        assertEquals(1, q.correct.size());
        assertEquals("Roma", q.answers.get(q.correct.get(0)));
        assertFalse(q.multi());
    }

    @Test
    public void asterisco_segnaLeRisposteCorrette() {
        List<Question> qs = Parser.parse("Quali sono pari?;1;*2;3;*4");
        Question q = qs.get(0);
        assertTrue(q.multi());
        assertEquals(2, q.correct.size());
        assertEquals("2", q.answers.get(q.correct.get(0)));
        assertEquals("4", q.answers.get(q.correct.get(1)));
    }

    @Test
    public void asteriscoProtetto_restaTestoNormale() {
        List<Question> qs = Parser.parse("Simbolo?;\\*asterisco;punto;virgola");
        Question q = qs.get(0);
        assertEquals("*asterisco", q.answers.get(0));
        assertEquals(0, (int) q.correct.get(0));
    }

    @Test
    public void spiegazione_eCommenti() {
        List<Question> qs = Parser.parse(
                "# questo e' un commento\n"
                        + "Perche'?;perche' si;perche' no;##Lo dice il manuale\n"
                        + "\n");
        assertEquals(1, qs.size());
        assertEquals("Lo dice il manuale", qs.get(0).explanation);
        assertEquals(2, qs.get(0).answers.size());
    }

    @Test
    public void csvConVirgole() {
        List<Question> qs = Parser.parse("Colore del cielo?,azzurro,verde,rosso");
        assertEquals(1, qs.size());
        assertEquals("azzurro", qs.get(0).answers.get(0));
    }

    @Test
    public void righeIncomplete_vengonoScartate() {
        List<Question> qs = Parser.parse("solo domanda\nDomanda;una sola risposta\nOk?;si;no");
        assertEquals(1, qs.size());
        assertEquals("Ok?", qs.get(0).text);
    }

    @Test
    public void andataERitorno_testoJsonTesto() throws Exception {
        String original = "Domanda A?;*esatta;errata;##spiegazione\nDomanda B?;uno;due;tre\n";
        List<Question> qs = Parser.parse(original);
        org.json.JSONArray arr = new org.json.JSONArray();
        for (Question q : qs) {
            arr.put(q.toJson());
        }
        List<Question> back = Parser.parse(arr.toString());
        assertEquals(qs.size(), back.size());
        assertEquals("spiegazione", back.get(0).explanation);
        assertEquals("esatta", back.get(0).answers.get(back.get(0).correct.get(0)));
        assertEquals(Parser.toText(qs), Parser.toText(back));
    }

    @Test
    public void idStabile_ignoraSpaziEMaiuscole() {
        Question a = Parser.parse("Che ora  e'?;tardi;presto").get(0);
        Question b = Parser.parse("che ora e'?;presto;tardi").get(0);
        assertEquals(a.id(), b.id());
    }

    @Test
    public void bancheReali_tutteLeRigheSonoDomandeValide() throws Exception {
        File dir = new File("src/main/assets/banks");
        assertTrue("cartella banchi non trovata: " + dir.getAbsolutePath(), dir.isDirectory());
        File[] files = dir.listFiles();
        assertTrue(files != null && files.length > 0);
        int totale = 0;
        for (File f : files) {
            String content = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            int righe = 0;
            for (String line : content.split("\r?\n")) {
                if (!line.trim().isEmpty() && !line.trim().startsWith("#")) {
                    righe++;
                }
            }
            List<Question> qs = Parser.parse(content);
            assertEquals("righe non interpretate in " + f.getName(), righe, qs.size());
            for (Question q : qs) {
                assertTrue(f.getName() + ": domanda senza risposte", q.answers.size() >= 2);
                assertTrue(f.getName() + ": nessuna risposta esatta", q.correct.size() >= 1);
            }
            totale += qs.size();
        }
        assertTrue("attese almeno 1000 domande, trovate " + totale, totale >= 1000);
    }
}
