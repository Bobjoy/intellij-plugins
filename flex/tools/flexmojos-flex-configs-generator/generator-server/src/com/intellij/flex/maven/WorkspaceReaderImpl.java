package com.intellij.flex.maven;

import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.repository.WorkspaceReader;
import org.sonatype.aether.repository.WorkspaceRepository;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

class WorkspaceReaderImpl implements WorkspaceReader {
  private static final String POM_EXTENSION = "pom";

  private final WorkspaceRepository repository = new WorkspaceRepository("ide", WorkspaceReaderImpl.class);

  private final HashMap<ArtifactKey, ArtifactData> map;
  private final GeneratorServer generatorServer;

  public WorkspaceReaderImpl(DataInputStream in, GeneratorServer generatorServer) throws IOException {
    this.generatorServer = generatorServer;
    
    final int mapSize = in.readUnsignedShort();
    map = new HashMap<ArtifactKey, ArtifactData>(mapSize);
    ObjectInputStream objectInputStream = new ObjectInputStream(in);
    try {
      for (int i = 0; i < mapSize; i++) {
        final ArtifactKey key = new ArtifactKey((String)objectInputStream.readObject(), (String)objectInputStream.readObject(),
                                                ((String)objectInputStream.readObject()));
        map.put(key, new ArtifactData(key, (String)objectInputStream.readObject()));
      }
    }
    catch (ClassNotFoundException e) {
      throw new IOException(e);
    }
  }

  @Override
  public WorkspaceRepository getRepository() {
    return repository;
  }

  @Override
  public File findArtifact(Artifact artifact) {
    final ArtifactData data = map.get(new ArtifactKey(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion()));
    if (data == null) {
       return null;
    }

    if (data.file == null) {
      data.file = new File(data.filePath);
    }

    final String ext = artifact.getExtension();
    if (!ext.equals(POM_EXTENSION)) {
      boolean isResourceBundle = false;
      if (!(ext.equals("swc") || (isResourceBundle = ext.equals("rb.swc")) || ext.equals("swf"))) {
        throw new IllegalStateException("Found artifact must be flash artifact (swc or swf): " + artifact);
      }

      if (data.outputFile == null) {
        try {
          generatorServer.resolveOutputs(data);
        }
        catch (Exception e) {
          generatorServer.getLogger().warn("Error while resolve output file for " + artifact.toString(), e);
          return null;
        }
      }

      if (isResourceBundle && artifact.getClassifier() != null) {
        return new File(data.localeOutputFilepathPattern.replace("{_locale_}", artifact.getClassifier()));
      }

      return data.outputFile;
    }

    return data.file;
  }

  @Override
  public List<String> findVersions(Artifact artifact) {
    final ArtifactData data = map.get(new ArtifactKey(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion()));
    if (data == null) {
      return Collections.emptyList();
    }

    return Collections.singletonList(artifact.getVersion());
  }

  private static class ArtifactKey {
    private final String groupId;
    private final String artifactId;
    private final String version;

    private ArtifactKey(String groupId, String artifactId, String version) {
      this.groupId = emptify(groupId);
      this.artifactId = emptify(artifactId);
      this.version = emptify(version);
    }

    private static String emptify(String str) {
      return str == null ? "" : str;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o instanceof ArtifactKey) {
        ArtifactKey other = (ArtifactKey)o;
        return groupId.equals(other.groupId) && artifactId.equals(other.artifactId) && version.equals(other.version);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return 31 * (31 * groupId.hashCode() + artifactId.hashCode()) + version.hashCode();
    }
  }

  static class ArtifactData {
    public final ArtifactKey key;
    private final String filePath;

    File file;
    File outputFile;
    String localeOutputFilepathPattern;

    private ArtifactData(ArtifactKey key, String filePath) {
      this.filePath = filePath;
      this.key = key;
    }
  }
}
