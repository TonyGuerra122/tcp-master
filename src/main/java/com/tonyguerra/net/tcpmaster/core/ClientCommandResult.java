package com.tonyguerra.net.tcpmaster.core;

import java.util.Optional;

public sealed interface ClientCommandResult permits ClientCommandResult.Send, ClientCommandResult.DontSend {
    record DontSend(Optional<String> localResponse) implements ClientCommandResult {
    }

    record Send(String messageToSend) implements ClientCommandResult {
        public Send {
            if (messageToSend == null || messageToSend.isBlank()) {
                throw new IllegalArgumentException("messageToSend must be not null/blank");
            }
        }
    }

    static ClientCommandResult dontSend() {
        return new DontSend(Optional.empty());
    }

    static ClientCommandResult dontSend(String localResponse) {
        return new DontSend(Optional.ofNullable(localResponse));
    }

    static ClientCommandResult send(String messageToSend) {
        return new Send(messageToSend);
    }
}
