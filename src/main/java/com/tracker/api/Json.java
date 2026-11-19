package com.tracker.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public final class Json {

    private Json() {
    }

    public static Gson create() {
        return new GsonBuilder()
                .registerTypeAdapter(LocalDate.class, new LocalDateAdapter().nullSafe())
                .registerTypeAdapter(BigDecimal.class, new BigDecimalAdapter().nullSafe())
                .create();
    }

    private static class LocalDateAdapter extends TypeAdapter<LocalDate> {

        @Override
        public void write(JsonWriter out, LocalDate value) throws IOException {
            out.value(value.toString());
        }

        @Override
        public LocalDate read(JsonReader in) throws IOException {
            try {
                return LocalDate.parse(in.nextString());
            } catch (DateTimeParseException e) {
                throw new JsonParseException("Некоректна дата", e);
            }
        }
    }

    private static class BigDecimalAdapter extends TypeAdapter<BigDecimal> {

        @Override
        public void write(JsonWriter out, BigDecimal value) throws IOException {
            BigDecimal v = value.scale() < 2 ? value.setScale(2) : value;
            out.jsonValue(v.toPlainString());
        }

        @Override
        public BigDecimal read(JsonReader in) throws IOException {
            try {
                return new BigDecimal(in.nextString());
            } catch (NumberFormatException e) {
                throw new JsonParseException("Некоректне число", e);
            }
        }
    }
}
