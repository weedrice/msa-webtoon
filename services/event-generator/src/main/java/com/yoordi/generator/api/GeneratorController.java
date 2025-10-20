package com.yoordi.generator.api;

import com.yoordi.generator.service.EventGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/generator")
@Tag(name = "Event Generator", description = "Control event generation for load testing")
public class GeneratorController {

    private final EventGenerationService generationService;

    public GeneratorController(EventGenerationService generationService) {
        this.generationService = generationService;
    }

    @PostMapping("/start")
    @Operation(summary = "Start event generation", description = "Begin generating events at configured EPS rate")
    public ResponseEntity<String> start() {
        generationService.start();
        return ResponseEntity.ok("Event generation started");
    }

    @PostMapping("/stop")
    @Operation(summary = "Stop event generation", description = "Stop generating events")
    public ResponseEntity<String> stop() {
        generationService.stop();
        return ResponseEntity.ok("Event generation stopped");
    }

    @GetMapping("/status")
    @Operation(summary = "Get generator status", description = "Get current status and statistics")
    public ResponseEntity<EventGenerationService.GeneratorStatus> getStatus() {
        return ResponseEntity.ok(generationService.getStatus());
    }
}