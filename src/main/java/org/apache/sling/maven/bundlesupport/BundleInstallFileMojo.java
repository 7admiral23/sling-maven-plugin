/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.sling.maven.bundlesupport;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

/**
 * Install an OSGi bundle from a given file path or Maven coordinates (resolved from the repository) to a running Sling instance.
 * One of the following parameter sets must be set to determine the bundle to install:
 * <ul>
 * <li>{@link #bundleFileName}</li>
 * <li>{@link #groupId}, {@link #artifactId}, {@link #version}, {@link #packaging} and optionally {@link #classifier}</li>
 * <li>{@link #artifact}</li>
 * </ul>
 * 
 * To install a bundle which has been built from the current Maven project rather use goal <a href="install-mojo.html">install</a>.
 * For details refer to <a href="bundle-installation.html">Bundle Installation</a>.
 */
@Mojo(name = "install-file", requiresProject = false)
public class BundleInstallFileMojo extends AbstractBundleInstallMojo {

    /**
     * The path of the bundle file to install.
     */
    @Parameter(property="sling.file")
    private File bundleFileName;

    /**
     * The groupId of the artifact to install
     */
    @Parameter(property="sling.groupId")
    private String groupId;

    /**
     * The artifactId of the artifact to install
     */
    @Parameter(property="sling.artifactId")
    private String artifactId;

    /**
     * The version of the artifact to install
     */
    @Parameter(property="sling.version")
    private String version;

    /**
     * The packaging of the artifact to install
     */
    @Parameter(property="sling.packaging", defaultValue="jar")
    private String packaging = "jar";

    /**
     * The classifier of the artifact to install
     */
    @Parameter(property="sling.classifier")
    private String classifier;

    /**
     * A string of the form {@code groupId:artifactId:version[:packaging[:classifier]]}.
     */
    @Parameter(property="sling.artifact")
    private String artifact;

    @Component
    private RepositorySystem repoSystem;

    @Parameter( defaultValue = "${repositorySystemSession}", readonly = true, required = true )
    private RepositorySystemSession repoSession;

    @Parameter( defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true )
    private List<RemoteRepository> repositories;

    @Override
    protected File getBundleFileName() throws MojoExecutionException {
        File fileName = bundleFileName;
        if (fileName == null) {
            fileName = resolveBundleFileFromArtifact();

            if (fileName == null) {
                throw new MojoExecutionException("Must provide either sling.file or sling.artifact parameters");
            }
        }

        return fileName;
    }

    private File resolveBundleFileFromArtifact() throws MojoExecutionException {
        if (artifactId == null && artifact == null) {
            return null;
        }
        if (artifactId == null) {
            String[] tokens = StringUtils.split(artifact, ":");
            if (tokens.length != 3 && tokens.length != 4 && tokens.length != 5) {
                throw new MojoExecutionException("Invalid artifact, you must specify "
                        + "groupId:artifactId:version[:packaging[:classifier]] " + artifact);
            }
            groupId = tokens[0];
            artifactId = tokens[1];
            version = tokens[2];
            if (tokens.length >= 4)
                packaging = tokens[3];
            if (tokens.length == 5)
                classifier = tokens[4];
        }

        File resolvedArtifactFile = resolveArtifact(new DefaultArtifact(groupId, artifactId, classifier, packaging, version));
        getLog().info("Resolved artifact to " + resolvedArtifactFile.getAbsolutePath());
        return resolvedArtifactFile;
    }

    protected File resolveArtifact(org.eclipse.aether.artifact.Artifact artifact) throws MojoExecutionException {
        ArtifactRequest req = new ArtifactRequest(artifact, getRemoteRepositoriesWithUpdatePolicy(repositories, RepositoryPolicy.UPDATE_POLICY_ALWAYS), null);
        ArtifactResult resolutionResult;
        try {
            resolutionResult = repoSystem.resolveArtifact(repoSession, req);
            return resolutionResult.getArtifact().getFile();
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException("Artifact " + artifact + " could not be resolved.", e);
        }
    }

    private List<RemoteRepository> getRemoteRepositoriesWithUpdatePolicy(List<RemoteRepository> repositories, String updatePolicy) {
        List<RemoteRepository> newRepositories = new ArrayList<>();
        for (RemoteRepository repo : repositories) {
            RemoteRepository.Builder builder = new RemoteRepository.Builder(repo);
            RepositoryPolicy newPolicy = new RepositoryPolicy(repo.getPolicy(false).isEnabled(), updatePolicy, repo.getPolicy(false).getChecksumPolicy());
            builder.setPolicy(newPolicy);
            newRepositories.add(builder.build());
        }
        return newRepositories;
    }
}