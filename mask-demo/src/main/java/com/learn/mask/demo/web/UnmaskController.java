package com.learn.mask.demo.web;

import com.learn.mask.crypto.UnmaskTicketService;
import com.learn.mask.crypto.UnmaskTicketService.TicketExpiredException;
import com.learn.mask.crypto.UnmaskTicketService.TicketPurposeMismatchException;
import com.learn.mask.crypto.UnmaskTicketService.UnmaskDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class UnmaskController {

    private final UnmaskTicketService tickets;

    public UnmaskController(UnmaskTicketService tickets) {
        this.tickets = tickets;
    }

    @PostMapping("/api/unmask")
    public Map<String, Object> unmask(@RequestBody UnmaskRequest request) {
        UnmaskTicketService.RevealResult result = tickets.revealForView(String.valueOf(request.userId()), request.field());
        return viewBody(request.userId(), result);
    }

    @PostMapping("/api/unmask/refresh")
    public Map<String, Object> refresh(@RequestBody TokenRequest request) {
        UnmaskTicketService.RevealResult result = tickets.refreshView(request.token());
        return viewBody(null, result);
    }

    @PostMapping("/api/unmask/dial")
    public Map<String, Object> dial(@RequestBody UnmaskRequest request) {
        String token = tickets.issueDialToken(String.valueOf(request.userId()), request.field());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", request.userId());
        body.put("field", request.field());
        body.put("token", token);
        return body;
    }

    @PostMapping("/api/unmask/redeem-dial")
    public Map<String, Object> redeemDial(@RequestBody TokenRequest request) {
        String value = tickets.redeemForDial(request.token());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("value", value);
        return body;
    }

    @ExceptionHandler(UnmaskDeniedException.class)
    public ResponseEntity<Map<String, String>> denied(UnmaskDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler({TicketExpiredException.class, TicketPurposeMismatchException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> badTicket(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    private Map<String, Object> viewBody(Long userId, UnmaskTicketService.RevealResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (userId != null) {
            body.put("userId", userId);
        }
        body.put("field", result.field());
        body.put("value", result.value());
        body.put("token", result.token());
        body.put("expiresAt", result.expiresAt().toString());
        return body;
    }

    public record UnmaskRequest(Long userId, String field) {
    }

    public record TokenRequest(String token) {
    }
}
