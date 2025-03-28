package org.bsassignment;

import com.browserstack.local.Local;
import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class UnifiedBrowserStackTest {
    // BrowserStack Credentials
    private static final String BROWSERSTACK_USERNAME = System.getProperty("browserstack.user", "USERNAME");
    private static final String BROWSERSTACK_ACCESS_KEY = System.getProperty("browserstack.key", "ACCESS_KEY");
    private static final String BROWSERSTACK_URL = "https://hub.browserstack.com/wd/hub";

    // Translate Service
    private static final Translate translate = TranslateOptions.getDefaultInstance().getService();

    // Local variables
    private WebDriver driver;
    private Local bsLocal;

    // Article Data Holder
    private static class ArticleData {
        private final String spanishTitle;
        private final String translatedTitle;
        private final String imageUrl;

        public ArticleData(String spanishTitle, String translatedTitle, String imageUrl) {
            this.spanishTitle = spanishTitle;
            this.translatedTitle = translatedTitle;
            this.imageUrl = imageUrl;
        }

        public String getTranslatedTitle() {
            return translatedTitle;
        }
    }

    // Translation Helper
    private String translateToEnglish(String text) {
        try {
            Translation translation = translate.translate(
                    text,
                    Translate.TranslateOption.sourceLanguage("es"),
                    Translate.TranslateOption.targetLanguage("en")
            );
            return translation.getTranslatedText();
        } catch (Exception e) {
            System.err.println("Translation error: " + e.getMessage());
            return text;
        }
    }

    // WebDriver Creation Method
    private WebDriver createWebDriver(String browser, boolean isBrowserStack) throws Exception {
        if (!isBrowserStack) {
            // Local WebDriver Setup
            switch (browser.toLowerCase()) {
                case "chrome":
                    WebDriverManager.chromedriver().setup();
                    ChromeOptions options = new ChromeOptions();
                    options.addArguments("--lang=es");
                    return new ChromeDriver(options);
                case "firefox":
                    WebDriverManager.firefoxdriver().setup();
                    return new FirefoxDriver();
                case "safari":
                    return new SafariDriver();
                case "edge":
                    WebDriverManager.edgedriver().setup();
                    return new EdgeDriver();
                default:
                    throw new IllegalArgumentException("Unsupported browser: " + browser);
            }
        }

        // BrowserStack WebDriver Setup
        DesiredCapabilities capabilities = new DesiredCapabilities();

        // BrowserStack Authentication
        capabilities.setCapability("browserstack.user", BROWSERSTACK_USERNAME);
        capabilities.setCapability("browserstack.key", BROWSERSTACK_ACCESS_KEY);

        // Project Details
        capabilities.setCapability("project", "El Pais Scraping");
        capabilities.setCapability("build", "Cross-Browser Test");
        capabilities.setCapability("name", "El Pais Article Scraper");

        // Browser-specific capabilities
        switch (browser.toLowerCase()) {
            case "chrome":
                capabilities.setCapability("browser", "Chrome");
                capabilities.setCapability("browser_version", "latest");
                capabilities.setCapability("os", "Windows");
                capabilities.setCapability("os_version", "11");
                break;
            case "firefox":
                capabilities.setCapability("browser", "Firefox");
                capabilities.setCapability("browser_version", "latest");
                capabilities.setCapability("os", "Windows");
                capabilities.setCapability("os_version", "11");
                break;
            case "edge":
                capabilities.setCapability("browser", "Edge");
                capabilities.setCapability("browser_version", "latest");
                capabilities.setCapability("os", "Windows");
                capabilities.setCapability("os_version", "11");
                break;
            case "iphone":
                capabilities.setCapability("device", "iPhone 14");
                capabilities.setCapability("real_mobile", "true");
                capabilities.setCapability("os_version", "16");
                break;
            case "android":
                capabilities.setCapability("device", "Samsung Galaxy S23");
                capabilities.setCapability("real_mobile", "true");
                capabilities.setCapability("os_version", "13.0");
                break;
            default:
                throw new IllegalArgumentException("Unsupported browser/device: " + browser);
        }

        // Create RemoteWebDriver for BrowserStack
        return new RemoteWebDriver(new URL(BROWSERSTACK_URL), capabilities);
    }

    // BrowserStack Local Setup
    @BeforeClass
    public void setupBrowserStackLocal() throws Exception {
        bsLocal = new Local();
        Map<String, String> options = new HashMap<>();
        options.put("key", BROWSERSTACK_ACCESS_KEY);
        options.put("verbose", "true");
        bsLocal.start(options);
    }

    // Cleanup BrowserStack Local
    @AfterClass
    public void teardownBrowserStackLocal() throws Exception {
        if (bsLocal != null) {
            bsLocal.stop();
        }
    }

    // Data Provider for Browser Configurations
    @DataProvider(name = "browserConfigurations", parallel = true)
    public Object[][] provideConfigurations() {
        return new Object[][] {
                // Local Browsers
                {"chrome", false},
                {"firefox", false},
                {"edge", false},

                // BrowserStack Browsers
                {"chrome", true},
                {"firefox", true},
                {"edge", true},
                {"iphone", true},
                {"android", true}
        };
    }

    // Main Test Method
    @Test(dataProvider = "browserConfigurations")
    public void testElPais(String browser, boolean isBrowserStack) throws Exception {
        // Create WebDriver
        driver = createWebDriver(browser, isBrowserStack);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

        try {
            // Navigate to El Pais
            driver.get("https://elpais.com/");
            driver.manage().window().maximize();

            // Handle Cookies
            handleCookiesPopup();

            // Validate Spanish Title
            validateSpanishTitle();

            // Navigate to Opinion Section
            navigateToOpinionSection();

            // Fetch and Process Articles
            List<ArticleData> articles = fetchAndProcessArticles();

            // Analyze Repeated Words
            analyzeRepeatedWords(articles.stream()
                    .map(ArticleData::getTranslatedTitle)
                    .collect(Collectors.toList()));

        } catch (Exception e) {
            System.err.println("Test failed for " + browser + ": " + e.getMessage());
            throw e;
        } finally {
            // Close WebDriver
            if (driver != null) {
                driver.quit();
            }
        }
    }

    // Cookies Handling Method
    private void handleCookiesPopup() {
        try {
            WebElement acceptCookies = new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(ExpectedConditions.elementToBeClickable(
                            By.xpath("//*[@id='didomi-notice-agree-button']")
                    ));
            acceptCookies.click();
        } catch (Exception e) {
            System.out.println("No cookies popup found or already handled.");
        }
    }

    // Spanish Title Validation Method
    private void validateSpanishTitle() {
        String title = driver.getTitle();
        System.out.println("Page Title: " + title);

        // Check for Spanish characters or words
        boolean isSpanish = title.matches(".*[áéíóúñ].*") ||
                title.toLowerCase().contains("país") ||
                title.toLowerCase().contains("opinión");

        Assert.assertTrue(isSpanish, "Title does not appear to be in Spanish: " + title);
        System.out.println("Title is in Spanish: " + title);
    }

    // Navigate to Opinion Section
    private void navigateToOpinionSection() {
        WebElement opinion = new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//a[@cmp-ltrk='portada_menu' and text()='Opinión']")
                ));
        opinion.click();
    }

    // Fetch and Process Articles
    private List<ArticleData> fetchAndProcessArticles() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        List<WebElement> articleElements = wait.until(
                ExpectedConditions.presenceOfAllElementsLocatedBy(
                        By.xpath("//div[contains(@class, 'b-d_d')]//article")
                )
        );

        List<ArticleData> articles = new ArrayList<>();
        int limit = Math.min(articleElements.size(), 5);

        for (int i = 0; i < limit; i++) {
            try {
                WebElement articleElement = articleElements.get(i);

                // Extract Spanish Title
                WebElement titleElement = articleElement.findElement(
                        By.xpath(".//header[contains(@class,'c_h')]//h2")
                );
                String spanishTitle = titleElement.getText();
                System.out.println("Original Title (Article " + (i + 1) + "): " + spanishTitle);

                // Translate Title
                String translatedTitle = translateToEnglish(spanishTitle);
                System.out.println("Translated Title (Article " + (i + 1) + "): " + translatedTitle);

                // Download Image
                String imageUrl = downloadArticleImage(articleElement, i + 1);

                // Create Article Data
                articles.add(new ArticleData(spanishTitle, translatedTitle, imageUrl));

            } catch (Exception e) {
                System.err.println("Error processing Article " + (i + 1) + ": " + e.getMessage());
            }
        }

        return articles;
    }

    // Download Article Image
    private String downloadArticleImage(WebElement articleElement, int articleNumber) {
        try {
            List<WebElement> imgElements = articleElement.findElements(
                    By.xpath(".//figure/a/img")
            );

            if (!imgElements.isEmpty()) {
                String imgUrl = imgElements.get(0).getAttribute("src");

                if (imgUrl != null && !imgUrl.isEmpty()) {
                    downloadImage(imgUrl, "Article_" + articleNumber + ".jpg");
                    System.out.println("Downloaded: Article_" + articleNumber + ".jpg");
                    return imgUrl;
                }
            }
        } catch (Exception e) {
            System.err.println("Image download failed for Article " + articleNumber + ": " + e.getMessage());
        }
        return null;
    }

    // Utility Method to Download Images
    public static void downloadImage(String imageUrl, String fileName) {
        String directoryPath = System.getProperty("user.home") + "/Article_images/";
        Path filePath = Paths.get(directoryPath + fileName);

        try {
            // Ensure directory exists
            Files.createDirectories(Paths.get(directoryPath));

            // Check if the file exists and delete it
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                System.out.println("Existing file deleted: " + filePath);
            }

            // Download the new image
            try (InputStream in = new URL(imageUrl).openStream()) {
                Files.copy(in, filePath);
                System.out.println("Image saved at: " + filePath);
            } catch (IOException e) {
                System.out.println("Failed to download " + fileName + ": " + e.getMessage());
            }
        } catch (IOException e) {
            System.out.println("Error handling file: " + fileName + ": " + e.getMessage());
        }
    }

    // Utility Method to Analyze Repeated Words
    public static void analyzeRepeatedWords(List<String> translatedHeaders) {
        Map<String, Integer> wordCounts = new HashMap<>();
        for (String header : translatedHeaders) {
            String[] words = header.split("\\s+");
            for (String word : words) {
                word = word.toLowerCase().replaceAll("[^a-zA-Z]", "");
                if (!word.isEmpty() && word.length() > 2) { // Ignore very short words
                    wordCounts.put(word, wordCounts.getOrDefault(word, 0) + 1);
                }
            }
        }

        System.out.println("Repeated Words (Appeared more than twice):");
        wordCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 2)
                .forEach(entry -> System.out.println(
                        entry.getKey() + ": " + entry.getValue() + " occurrences"
                ));
    }

    // Parallel Execution Method
    @Test
    public void runParallelTests() throws InterruptedException {
        String[] browsers = {"chrome", "firefox", "edge", "iphone", "android"};
        ExecutorService executor = Executors.newFixedThreadPool(browsers.length);

        for (String browser : browsers) {
            executor.submit(() -> {
                try {
                    testElPais(browser, true);
                } catch (Exception e) {
                    System.err.println("Error in parallel test for " + browser + ": " + e.getMessage());
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.MINUTES);
    }
}