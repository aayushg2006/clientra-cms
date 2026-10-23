package io.clientra.controller;

import io.clientra.dto.CustomerDtos.TagDto;
import io.clientra.entity.Tag;
import io.clientra.exception.BusinessRuleException;
import io.clientra.repository.TagRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Tags")
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagRepository tagRepo;

    public record TagRequest(@NotBlank @Size(max = 50) String name,
                             @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Use a hex colour like #14776B") String color) {
    }

    @GetMapping
    public List<TagDto> list() {
        return tagRepo.findAllByOrderByName().stream().map(t -> new TagDto(t.getId(), t.getName(), t.getColor())).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','SALES_EXEC')")
    @Transactional
    public TagDto create(@Valid @RequestBody TagRequest req) {
        if (tagRepo.findByNameIgnoreCase(req.name().trim()).isPresent()) {
            throw new BusinessRuleException("Tag already exists");
        }
        Tag t = tagRepo.save(new Tag(req.name().trim(), req.color() != null ? req.color() : "#14776B"));
        return new TagDto(t.getId(), t.getName(), t.getColor());
    }
}
