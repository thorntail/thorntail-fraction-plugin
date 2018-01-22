/**
 * Copyright 2015-2016 Red Hat, Inc, and individual contributors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.swarm.plugin;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.eclipse.aether.repository.RemoteRepository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Michal Szynkiewicz, michal.l.szynkiewicz@gmail.com
 * <br>
 * Date: 1/22/18
 */
public class RepositoryUtils {

    private RepositoryUtils() {
    }

    public static List<RemoteRepository> prepareRepositories(List<ArtifactRepository> remoteRepositories) {
        return remoteRepositories.stream()
                .map(RepositoryUtils::artifactRepositoryToRemoteRepository)
                .collect(Collectors.toList());
    }

    protected static RemoteRepository artifactRepositoryToRemoteRepository(ArtifactRepository remoteRepository) {
        return new RemoteRepository.Builder(
                remoteRepository.getId(),
                "default",
                remoteRepository.getUrl()
        ).build();
    }
}
