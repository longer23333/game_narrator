package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.InputStream;
import org.junit.Test;

public final class Gpt2TokenizerTest {
    private static Gpt2Tokenizer tokenizer() throws Exception {
        try (InputStream vocab = Gpt2TokenizerTest.class.getClassLoader().getResourceAsStream("gpt2/vocab.json");
             InputStream merges = Gpt2TokenizerTest.class.getClassLoader().getResourceAsStream("gpt2/merges.txt")) {
            return new Gpt2Tokenizer(vocab, merges);
        }
    }

    @Test public void helloWorldMatchesKnownGpt2Ids() throws Exception {
        Gpt2Tokenizer tokenizer = tokenizer();
        assertArrayEquals(new int[]{15496, 995}, tokenizer.encode("Hello world"));
    }

    @Test public void roundTripPreservesText() throws Exception {
        Gpt2Tokenizer tokenizer = tokenizer();
        String text = "The quick brown fox jumps over 13 lazy dogs.";
        assertEquals(text, tokenizer.decode(tokenizer.encode(text)));
    }
}
