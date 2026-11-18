/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.agent.prompt;

import dev.jahaziel.agent.AgentException;
import dev.jahaziel.agent.AgentStore;
import dev.jahaziel.identity.domain.UserId;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CRUD for admin-managed global templates and per-user snippets. Only the two supported variables,
 * {@code ${file}} and {@code ${selection}}, are accepted in a template body.
 */
public final class PromptLibrary {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^{}]*)\\}");
    private static final String FILE_VARIABLE = "${file}";
    private static final String SELECTION_VARIABLE = "${selection}";
    private static final int MAX_BODY_LENGTH = 8000;

    private final AgentStore store;

    public PromptLibrary(AgentStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /** Creates an admin-managed global template. */
    public PromptTemplate createGlobal(String name, String body) {
        return create(null, name, body);
    }

    /** Creates a snippet owned by one user. */
    public PromptTemplate createUser(UserId user, String name, String body) {
        requireUser(user);
        return create(user.value().toString(), name, body);
    }

    /** Returns global templates followed by the user's snippets. */
    public List<PromptTemplate> list(UserId user) {
        requireUser(user);
        List<PromptTemplate> result = new ArrayList<PromptTemplate>(loadGlobals().templates);
        result.addAll(loadUser(user).templates);
        return result;
    }

    /** Returns one global template by id. */
    public PromptTemplate getGlobal(String id) {
        return find(loadGlobals(), id);
    }

    /** Returns one user-owned snippet by id. */
    public PromptTemplate getUser(UserId user, String id) {
        requireUser(user);
        return find(loadUser(user), id);
    }

    /** Updates an admin-managed global template. */
    public PromptTemplate updateGlobal(String id, String name, String body) {
        TemplateIndex index = loadGlobals();
        PromptTemplate template = find(index, id);
        apply(template, name, body);
        store.write(globalPath(), index);
        return template;
    }

    /** Updates one user's own snippet. */
    public PromptTemplate updateUser(UserId user, String id, String name, String body) {
        requireUser(user);
        TemplateIndex index = loadUser(user);
        PromptTemplate template = find(index, id);
        apply(template, name, body);
        store.write(userPath(user), index);
        return template;
    }

    /** Deletes an admin-managed global template. */
    public void deleteGlobal(String id) {
        TemplateIndex index = loadGlobals();
        find(index, id);
        remove(index, id);
        store.write(globalPath(), index);
    }

    /** Deletes one user's own snippet. */
    public void deleteUser(UserId user, String id) {
        requireUser(user);
        TemplateIndex index = loadUser(user);
        find(index, id);
        remove(index, id);
        store.write(userPath(user), index);
    }

    /**
     * Renders a template, replacing declared variables with the supplied values. Values are inserted
     * verbatim in one pass and are never re-expanded.
     */
    public String render(UserId user, String id, String file, String selection) {
        requireUser(user);
        TemplateIndex userIndex = loadUser(user);
        PromptTemplate template = contains(userIndex, id)
            ? find(userIndex, id)
            : find(loadGlobals(), id);
        String body = template.getBody();
        StringBuilder result = new StringBuilder(body.length() + 128);
        int index = 0;
        while (index < body.length()) {
            if (body.startsWith(FILE_VARIABLE, index)) {
                result.append(file == null ? "" : file);
                index += FILE_VARIABLE.length();
            } else if (body.startsWith(SELECTION_VARIABLE, index)) {
                result.append(selection == null ? "" : selection);
                index += SELECTION_VARIABLE.length();
            } else {
                result.append(body.charAt(index));
                index++;
            }
        }
        return result.toString();
    }

    private PromptTemplate create(String owner, String name, String body) {
        PromptTemplate template = new PromptTemplate();
        template.setId(UUID.randomUUID().toString());
        template.setOwner(owner);
        String now = Instant.now().toString();
        template.setCreatedAt(now);
        template.setUpdatedAt(now);
        apply(template, name, body);
        TemplateIndex index = owner == null ? loadGlobals() : loadUserIndex(owner);
        index.templates.add(template);
        save(owner, index);
        return template;
    }

    private static void apply(PromptTemplate template, String name, String body) {
        if (name == null || !name.matches("[A-Za-z0-9][A-Za-z0-9 _.-]{0,79}")) {
            throw new AgentException("prompt name must be 1 to 80 safe characters");
        }
        validateBody(body);
        template.setName(name);
        template.setBody(body);
        template.setUpdatedAt(Instant.now().toString());
    }

    private static void validateBody(String body) {
        if (body == null || body.isEmpty() || body.length() > MAX_BODY_LENGTH) {
            throw new AgentException("prompt body must be 1 to 8000 characters");
        }
        Matcher matcher = PLACEHOLDER.matcher(body);
        while (matcher.find()) {
            String variable = matcher.group(1);
            if (!"file".equals(variable) && !"selection".equals(variable)) {
                throw new AgentException("unsupported prompt variable: " + variable);
            }
        }
        String stripped = matcher.replaceAll("");
        if (stripped.indexOf("${") >= 0 || stripped.indexOf('}') >= 0) {
            throw new AgentException("prompt body has malformed variables");
        }
    }

    private static PromptTemplate find(TemplateIndex index, String id) {
        if (id == null || id.isEmpty()) {
            throw new AgentException("prompt id is required");
        }
        for (PromptTemplate template : index.templates) {
            if (id.equals(template.getId())) {
                return template;
            }
        }
        throw new AgentException("unknown prompt template");
    }

    private static boolean contains(TemplateIndex index, String id) {
        for (PromptTemplate template : index.templates) {
            if (id.equals(template.getId())) {
                return true;
            }
        }
        return false;
    }

    private static void remove(TemplateIndex index, String id) {
        List<PromptTemplate> kept = new ArrayList<PromptTemplate>();
        for (PromptTemplate template : index.templates) {
            if (!id.equals(template.getId())) {
                kept.add(template);
            }
        }
        index.templates = kept;
    }

    private TemplateIndex loadGlobals() {
        return load(globalPath());
    }

    private TemplateIndex loadUser(UserId user) {
        return loadUserIndex(user.value().toString());
    }

    private TemplateIndex loadUserIndex(String owner) {
        return load(userPath(owner));
    }

    private TemplateIndex load(Path path) {
        TemplateIndex index = store.read(path, TemplateIndex.class);
        if (index == null) {
            index = new TemplateIndex();
        }
        if (index.templates == null) {
            index.templates = new ArrayList<PromptTemplate>();
        }
        return index;
    }

    private void save(String owner, TemplateIndex index) {
        store.write(owner == null ? globalPath() : userPath(owner), index);
    }

    private Path globalPath() {
        return store.containedChild(store.promptRoot(), "global.json");
    }

    private Path userPath(UserId user) {
        return userPath(user.value().toString());
    }

    private Path userPath(String owner) {
        return store.containedChild(store.promptRoot(), "users", owner + ".json");
    }

    private static void requireUser(UserId user) {
        Objects.requireNonNull(user, "user");
    }

    /** Persisted prompt index. */
    public static final class TemplateIndex {
        private List<PromptTemplate> templates = new ArrayList<PromptTemplate>();

        public List<PromptTemplate> getTemplates() {
            return templates;
        }

        public void setTemplates(List<PromptTemplate> templates) {
            this.templates = templates;
        }
    }
}
