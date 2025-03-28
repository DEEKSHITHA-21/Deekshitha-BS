package org.bsassignment;

import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;

public class GoogleTranslateHelper {

    // Method to translate text from Spanish to English
    public static String translateToEnglish(String text) {
        // Setup Google Translate API
        Translate translate = TranslateOptions.getDefaultInstance().getService();

        // Perform translation from Spanish to English
        Translation translation = translate.translate(text,
                Translate.TranslateOption.sourceLanguage("es"),
                Translate.TranslateOption.targetLanguage("en"));

        return translation.getTranslatedText();
    }
}
