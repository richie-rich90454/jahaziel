/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.agent.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.jahaziel.agent.AgentException;
import dev.jahaziel.agent.AgentStore;
import dev.jahaziel.identity.domain.UserId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class PromptLibraryTest {
    @TempDir Path temporary;

    private final UserId owner = new UserId(UUID.randomUUID());
    private final UserId other = new UserId(UUID.randomUUID());

    @Test
    void globalAndUserTemplatesAreCrudManaged() {
        PromptLibrary library = library();

        PromptTemplate global = library.createGlobal("Review", "Review ${file}");
        PromptTemplate snippet = library.createUser(owner, "Explain", "Explain ${selection}");

        assertEquals(2, library.list(owner).size());
        assertNotNull(library.getGlobal(global.getId()));
        assertNotNull(library.getUser(owner, snippet.getId()));

        library.updateGlobal(global.getId(), "Review2", "Review ${file} carefully");
        library.updateUser(owner, snippet.getId(), "Explain2", "Explain ${selection} briefly");
        assertEquals("Review ${file} carefully", library.getGlobal(global.getId()).getBody());

        library.deleteUser(owner, snippet.getId());
        assertEquals(1, library.list(owner).size());
        library.deleteGlobal(global.getId());
        assertEquals(0, library.list(owner).size());
    }

    @Test
    void renderSubstitutesSupportedVariablesInOnePass() {
        PromptLibrary library = library();
        PromptTemplate template =
            library.createUser(owner, "Ctx", "File ${file} has ${selection}");

        String rendered =
            library.render(owner, template.getId(), "${selection}", "selected text");

        assertEquals("File ${selection} has selected text", rendered);
    }

    @Test
    void unknownVariablesAndMalformedBracesAreRejected() {
        final PromptLibrary library = library();
        assertThrowsAgent(
            new Executable() {
                @Override
                public void execute() {
                    library.createUser(owner, "Bad", "Run ${shell}");
                }
            });
        assertThrowsAgent(
            new Executable() {
                @Override
                public void execute() {
                    library.createUser(owner, "Bad", "Run ${file");
                }
            });
        assertThrowsAgent(
            new Executable() {
                @Override
                public void execute() {
                    library.createUser(owner, "Bad", "Run file}");
                }
            });
    }

    @Test
    void userCannotReadOrDeleteAnotherUsersSnippet() {
        final PromptLibrary library = library();
        final PromptTemplate mine = library.createUser(owner, "Mine", "My ${file}");

        assertThrowsAgent(
            new Executable() {
                @Override
                public void execute() {
                    library.getUser(other, mine.getId());
                }
            });
        assertThrowsAgent(
            new Executable() {
                @Override
                public void execute() {
                    library.deleteUser(other, mine.getId());
                }
            });
        List<PromptTemplate> others = library.list(other);
        assertEquals(0, others.size());
    }

    @Test
    void promptsStayUnderTheDataDirectory() {
        Path data = temporary.resolve("data");
        PromptLibrary library = library();
        library.createGlobal("Global", "Body");
        library.createUser(owner, "Mine", "Body");

        Path global = data.resolve("agent").resolve("prompts").resolve("global.json");
        Path user =
            data.resolve("agent").resolve("prompts").resolve("users")
                .resolve(owner.value().toString() + ".json");
        assertTrue(Files.isRegularFile(global));
        assertTrue(Files.isRegularFile(user));
        assertTrue(user.toAbsolutePath().startsWith(data.toAbsolutePath()));
    }

    private PromptLibrary library() {
        return new PromptLibrary(new AgentStore(temporary.resolve("data"), new ObjectMapper()));
    }

    private static void assertThrowsAgent(Executable executable) {
        try {
            executable.execute();
            fail("expected an AgentException");
        } catch (Throwable failure) {
            assertTrue(failure instanceof AgentException);
        }
    }
}
