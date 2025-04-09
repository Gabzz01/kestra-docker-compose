package fr.rtz.kestra.docker.compose;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public enum RemoveImagesOptions {
    @JsonProperty("local")
    LOCAL("local"),
    @JsonProperty("all")
    ALL("all");

    private final String value;

    RemoveImagesOptions(String value) {
        this.value = value;
    }
}