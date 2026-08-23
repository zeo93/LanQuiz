package com.marco.lanquiz;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Il raggruppamento in home dipende solo dal nome del file: va verificato. */
public class BankTest {

    @Test
    public void categorie_deiBanchiPreinstallati() {
        assertEquals("Google Engineering", Bank.categoryOf("Google_Engineering_01"));
        assertEquals("Google Engineering", Bank.categoryOf("Google_Engineering_00_full"));
        assertEquals("Google Leader", Bank.categoryOf("Google_Leader_06"));
        assertEquals("Google Generative AI", Bank.categoryOf("Google_Generative_AI_01"));
    }

    @Test
    public void nomeSenzaFamiglia_finisceInAltriQuiz() {
        assertEquals("Altri quiz", Bank.categoryOf("questions"));
        assertEquals("Altri quiz", Bank.categoryOf("quiz_02"));
    }

    @Test
    public void titoloLeggibile_senzaEstensione() {
        assertEquals("Google Leader 01", new Bank("Google_Leader_01.txt", true).title);
        assertEquals("Google Leader", new Bank("Google_Leader_01.txt", true).category);
    }

    @Test
    public void nomiDiFileRipuliti() {
        assertEquals("mio_quiz.txt", Banks.sanitize("mio/quiz"));
        assertEquals("dati.csv", Banks.sanitize("dati.csv"));
        assertEquals("quiz.txt", Banks.sanitize("   "));
    }
}
