/*
 * Copyright The Cryostat Authors
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.cryostat.recordings;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.FileSystem;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.tuple.Pair;

public class RecordingMetadataManager {

    public static final String NOTIFICATION_CATEGORY = "RecordingMetadataUpdated";

    private final Path recordingMetadataDir;
    private final FileSystem fs;
    private final Gson gson;
    private final Base32 base32;
    private final Logger logger;

    private final Map<Pair<String, String>, Metadata> recordingMetadataMap;

    RecordingMetadataManager(
            Path recordingMetadataDir, FileSystem fs, Gson gson, Base32 base32, Logger logger) {
        this.recordingMetadataDir = recordingMetadataDir;
        this.fs = fs;
        this.gson = gson;
        this.base32 = base32;
        this.logger = logger;
        this.recordingMetadataMap = new ConcurrentHashMap<>();
    }

    public void load() throws IOException {
        this.fs.listDirectoryChildren(recordingMetadataDir).stream()
                .peek(n -> logger.trace("Recording Metadata file: {}", n))
                .map(recordingMetadataDir::resolve)
                .map(
                        path -> {
                            try {
                                return fs.readFile(path);
                            } catch (IOException e) {
                                logger.warn(e);
                                return null;
                            }
                        })
                .filter(Objects::nonNull)
                .map(reader -> gson.fromJson(reader, StoredRecordingMetadata.class))
                .forEach(
                        srm ->
                                recordingMetadataMap.put(
                                        Pair.of(srm.getTargetId(), srm.getRecordingName()), srm));
    }

    public Future<Metadata> setRecordingMetadata(
            String targetId, String recordingName, Metadata metadata) throws IOException {
        Objects.requireNonNull(targetId);
        Objects.requireNonNull(recordingName);
        Objects.requireNonNull(metadata);
        this.recordingMetadataMap.put(Pair.of(targetId, recordingName), metadata);
        fs.writeString(
                this.getMetadataPath(targetId, recordingName),
                gson.toJson(StoredRecordingMetadata.of(targetId, recordingName, metadata)),
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
        return CompletableFuture.completedFuture(metadata);
    }

    public Future<Metadata> setRecordingMetadata(String recordingName, Metadata metadata)
            throws IOException {
        Objects.requireNonNull(recordingName);
        Objects.requireNonNull(metadata);
        return this.setRecordingMetadata(RecordingArchiveHelper.ARCHIVES, recordingName, metadata);
    }

    public Metadata getMetadata(String targetId, String recordingName) {
        Objects.requireNonNull(targetId);
        Objects.requireNonNull(recordingName);
        return this.recordingMetadataMap.computeIfAbsent(
                Pair.of(targetId, recordingName), k -> new Metadata());
    }

    public Metadata deleteRecordingMetadataIfExists(String targetId, String recordingName)
            throws IOException {
        Objects.requireNonNull(targetId);
        Objects.requireNonNull(recordingName);
        Metadata deleted = this.recordingMetadataMap.remove(Pair.of(targetId, recordingName));
        fs.deleteIfExists(this.getMetadataPath(targetId, recordingName));
        return deleted;
    }

    public Future<Metadata> copyMetadataToArchives(
            String targetId, String recordingName, String filename) throws IOException {
        Objects.requireNonNull(targetId);
        Objects.requireNonNull(recordingName);
        Objects.requireNonNull(filename);
        Metadata metadata = this.getMetadata(targetId, recordingName);
        return this.setRecordingMetadata(filename, metadata);
    }

    public Map<String, String> parseRecordingLabels(String labels) throws IllegalArgumentException {
        Objects.requireNonNull(labels, "Labels must not be null");

        try {
            Type mapType = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> parsedLabels = gson.fromJson(labels, mapType);
            if (parsedLabels == null) {
                throw new IllegalArgumentException(labels);
            }
            Pattern noWhitespace = Pattern.compile("^(\\S)+$");

            for (var label : parsedLabels.entrySet()) {
                Matcher keyMatcher = noWhitespace.matcher(label.getKey());
                Matcher valueMatcher = noWhitespace.matcher(label.getValue());

                if (!keyMatcher.matches() || !valueMatcher.matches()) {
                    throw new IllegalArgumentException("Labels must not contain whitespace");
                }
            }
            return parsedLabels;
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private Path getMetadataPath(String targetId, String recordingName) {
        String filename = String.format("%s%s", targetId, recordingName);
        return recordingMetadataDir.resolve(
                base32.encodeAsString(filename.getBytes(StandardCharsets.UTF_8)) + ".json");
    }

    private static class StoredRecordingMetadata extends Metadata {
        private final String targetId;
        private final String recordingName;

        StoredRecordingMetadata(String targetId, String recordingName, Map<String, String> labels) {
            super(labels);
            this.targetId = targetId;
            this.recordingName = recordingName;
        }

        static StoredRecordingMetadata of(
                String targetId, String recordingName, Metadata metadata) {
            return new StoredRecordingMetadata(targetId, recordingName, metadata.getLabels());
        }

        String getTargetId() {
            return this.targetId;
        }

        String getRecordingName() {
            return this.recordingName;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null) {
                return false;
            }
            if (o == this) {
                return true;
            }
            if (!(o instanceof StoredRecordingMetadata)) {
                return false;
            }

            StoredRecordingMetadata srm = (StoredRecordingMetadata) o;
            return new EqualsBuilder()
                    .appendSuper(super.equals(o))
                    .append(targetId, srm.targetId)
                    .append(recordingName, srm.recordingName)
                    .build();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder()
                    .appendSuper(super.hashCode())
                    .append(targetId)
                    .append(recordingName)
                    .toHashCode();
        }
    }

    public static class Metadata {
        protected final Map<String, String> labels;

        public Metadata() {
            this.labels = new ConcurrentHashMap<>();
        }

        public Metadata(Metadata o) {
            this.labels = new ConcurrentHashMap<>(o.labels);
        }

        public Metadata(Map<String, String> labels) {
            this.labels = new ConcurrentHashMap<>(labels);
        }

        public Map<String, String> getLabels() {
            return new ConcurrentHashMap<>(labels);
        }

        @Override
        public boolean equals(Object o) {
            if (o == null) {
                return false;
            }
            if (o == this) {
                return true;
            }
            if (!(o instanceof Metadata)) {
                return false;
            }

            Metadata metadata = (Metadata) o;
            return new EqualsBuilder().append(labels, metadata.labels).build();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder().append(labels).toHashCode();
        }
    }
}
