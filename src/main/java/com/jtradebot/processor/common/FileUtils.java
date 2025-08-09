package com.jtradebot.processor.common;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class FileUtils {


    private static final String ACCESS_TOKEN_FOLDER_BASE_PATH = "src/main/resources/tokens/";
    private static final String ACCESS_TOKEN_FILE_RELATIVE_PATH = "/accessToken.txt";

    private static String getDateWiseFolderPath() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        String date = now.format(formatter);
        return ACCESS_TOKEN_FOLDER_BASE_PATH + date;
    }

    public static void writeAccessTokenToFile(String accessToken) {
        String folderPath = getDateWiseFolderPath();
        File directory = new File(folderPath);
        if (!directory.exists()) {
            directory.mkdirs();
        }

        try (FileWriter writer = new FileWriter(folderPath + ACCESS_TOKEN_FILE_RELATIVE_PATH)) {
            writer.write(accessToken);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write access token to file", e);
        }
    }

    public static String readAccessTokenFromFile() {
        String folderPath = getDateWiseFolderPath();
        String filePath = folderPath + ACCESS_TOKEN_FILE_RELATIVE_PATH;

        if (!Files.exists(Paths.get(filePath))) {
            throw new RuntimeException("Access token file does not exist");
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            return reader.readLine();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read access token from file", e);
        }
    }


}
