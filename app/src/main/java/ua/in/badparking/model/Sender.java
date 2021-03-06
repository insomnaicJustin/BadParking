package ua.in.badparking.model;

import com.google.gson.Gson;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.Headers;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.MultipartBuilder;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.apache.commons.io.FileUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import ua.in.badparking.data.Report;

/**
 * Created by Dima Kovalenko on 8/15/15.
 */
public enum Sender {
    INST;

    public static final int CODE_UPLOADING_PHOTO = 9001;
    public static final int CODE_FILE_NOT_FOUND = 9002;
    public static final int CODE_UNKNOWN_ERROR = 9003;
    public static final int CODE_PARSING_FAILED = 9004;
    public static final int CODE_FILE_READING_ERROR = 9005;

    public static final String POST_URL = "http://badparking.in.ua/modules/json.php";

    private static final MediaType MEDIA_TYPE_PNG = MediaType.parse("image/png");
    private static final MediaType MEDIA_TYPE_JPG = MediaType.parse("image/jpeg");

    private final OkHttpClient client = new OkHttpClient();

    public void send(final SendCallback sendCallback) {
        client.setConnectTimeout(20, TimeUnit.SECONDS);
        client.setReadTimeout(50, TimeUnit.SECONDS);
        client.setWriteTimeout(50, TimeUnit.SECONDS);
        final Report report = ReportController.INST.getReport();
        final String json = new Gson().toJson(report);
        RequestBody formBody = new FormEncodingBuilder()
                .add("cmd", "save")
                .add("data", json)
                .build();
        Request request = new Request.Builder()
                .url(POST_URL)
                .post(formBody)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Response response) throws IOException {
                if (report.getPhotoFiles().size() != 0) {
                    try {
                        final String responseString = response.body().string();
                        JSONObject json = new JSONObject(responseString);
                        uploadPhoto(json.getInt("id"), 0, sendCallback);
                        sendCallback.onCallback(CODE_UPLOADING_PHOTO, "Завантажується фото №1...");
                    } catch (JSONException e) {
                        sendCallback.onCallback(CODE_PARSING_FAILED, "Помилка парсингу.");
                        e.printStackTrace();
                    }
                } else {
                    sendCallback.onCallback(response.code(), "");
                }
            }

            @Override
            public void onFailure(Request request, IOException e) {
                sendCallback.onCallback(CODE_UNKNOWN_ERROR, e.getMessage());
            }
        });

    }

    private void uploadPhoto(final int sessionId, final int imageIndex, final SendCallback sendCallback) throws FileNotFoundException {
        final Report report = ReportController.INST.getReport();
        File image = report.getPhotoFiles().get(imageIndex);
        RequestBody formBody = new FormEncodingBuilder()
                .add("cmd", "upload")
                .add("id", String.valueOf(sessionId))
                .build();
        final MediaType mediaType = image.getName().endsWith("png") ? MEDIA_TYPE_PNG : MEDIA_TYPE_JPG;
        RequestBody requestBody = null;
        try {
            requestBody = new MultipartBuilder()
                    .type(MultipartBuilder.FORM)
                    .addPart(formBody)
                    .addPart(
                            Headers.of("Content-Disposition", "form-data; name=\"file\""),
                            RequestBody.create(mediaType, FileUtils.readFileToByteArray(image)))
                    .build();
        } catch (IOException e) {
            sendCallback.onCallback(CODE_FILE_READING_ERROR, " Помилка при обробцi фото.");
            e.printStackTrace();
            return;
        }

        Request request = new Request.Builder()
                .url(POST_URL)
                .post(requestBody)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Response response) throws IOException {
                sendCallback.onCallback(response.code(), "");
                final int numberOfPhotos = report.getPhotoFiles().size();
                if (numberOfPhotos != 0 && imageIndex + 1 < numberOfPhotos) {
                    try {
                        uploadPhoto(sessionId, imageIndex + 1, sendCallback);
                        sendCallback.onCallback(CODE_UPLOADING_PHOTO, "Завантажується фото №" + (imageIndex + 2) + "...");
                    } catch (FileNotFoundException e) {
                        sendCallback.onCallback(CODE_FILE_NOT_FOUND, "Фото не знайдено.");
                        e.printStackTrace();
                    }
                } else {
                    sendCallback.onCallback(response.code(), "");
                }
            }

            @Override
            public void onFailure(Request request, IOException e) {
                sendCallback.onCallback(CODE_UNKNOWN_ERROR, e.getMessage());
            }
        });

    }

    public interface SendCallback {
        public void onCallback(int code, String message);
    }
}
