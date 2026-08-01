package me.cresterida.nomos.dto;

public record ProxyAppResponse(
        String appId,
        String audience,
        String idp
) {}
