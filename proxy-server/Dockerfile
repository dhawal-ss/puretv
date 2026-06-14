# SECTION 09 — multi-stage build keeps the runtime image to a static binary on
# `scratch` (no shell, no package manager, nothing for an attacker to land on —
# this server is meant to be exposed to the internet by end users who likely
# aren't security specialists, so the smallest possible attack surface matters).

FROM golang:1.23.12-alpine AS build
RUN apk add --no-cache ca-certificates
WORKDIR /src
COPY go.mod ./
COPY *.go ./
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o /out/proxy-server .

FROM scratch AS runtime
# CA certs are required for the outbound HTTPS fetch to Twitch's CDN.
COPY --from=build /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/ca-certificates.crt
COPY --from=build /out/proxy-server /proxy-server

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["/proxy-server"]
