package secretchat.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LinkUtilsTest {
    @Test
    void findsSupportedLinksAndRemovesTrailingPunctuation() {
        List<String> links = LinkUtils.findLinks(
                        "Xem https://example.com/docs, www.gitlab.com và handbook.example.org/page.")
                .stream()
                .map(LinkUtils.LinkMatch::value)
                .toList();

        assertEquals(List.of(
                "https://example.com/docs",
                "www.gitlab.com",
                "handbook.example.org/page"), links);
    }

    @Test
    void normalizesLinksWithoutScheme() {
        assertEquals("https://www.gitlab.com", LinkUtils.normalize("www.gitlab.com"));
        assertEquals("https://gitlab.com", LinkUtils.normalize("gitlab.com"));
        assertEquals("http://gitlab.com", LinkUtils.normalize("http://gitlab.com"));
    }
}
