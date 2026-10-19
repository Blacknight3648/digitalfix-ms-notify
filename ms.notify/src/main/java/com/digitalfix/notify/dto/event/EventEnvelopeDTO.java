package com.digitalfix.notify.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventEnvelopeDTO<T> implements Serializable {

    private String eventId;
    private String eventType;
    private LocalDateTime timestamp;
    private String traceId;
    private String correlationId;
    private T payload;

    public T getData() {
        return payload;
    }

    public void setData(T data) {
        this.payload = data;
    }
}
