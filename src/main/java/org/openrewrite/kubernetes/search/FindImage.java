/*
 *  Copyright 2021 the original author or authors.
 *  <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  <p>
 *  https://www.apache.org/licenses/LICENSE-2.0
 *  <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.openrewrite.kubernetes.search;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.kubernetes.ContainerImage;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.YamlVisitor;
import org.openrewrite.yaml.search.YamlSearchResult;
import org.openrewrite.yaml.tree.Yaml;

import static org.openrewrite.kubernetes.tree.K8S.Containers.inContainerSpec;
import static org.openrewrite.kubernetes.tree.K8S.Containers.isImageName;
import static org.openrewrite.kubernetes.tree.K8S.InitContainers.inInitContainerSpec;
import static org.openrewrite.kubernetes.tree.K8S.*;

@Value
@EqualsAndHashCode(callSuper = true)
public class FindImage extends Recipe {

    @Option(displayName = "Repository",
            description = "The repository part of the image name to search for in containers and initContainers.",
            example = "gcr.io",
            required = false)
    @Nullable
    String repository;

    @Option(displayName = "Image name",
            description = "The image name to search for in containers and initContainers.",
            example = "nginx")
    String imageName;

    @Option(displayName = "Image tag",
            description = "The tag part of the image name to search for in containers and initContainers.",
            example = "v1.2.3",
            required = false)
    @Nullable
    String imageTag;

    @Option(displayName = "Include initContainers",
            description = "Boolean to indicate whether or not to treat initContainers/image identically to " +
                    "containers/image.")
    boolean includeInitContainers;

    @Override
    public String getDisplayName() {
        return "Image name";
    }

    @Override
    public String getDescription() {
        return "The image name to search for in containers and initContainers.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        YamlSearchResult result = new YamlSearchResult(this);
        return new YamlVisitor<ExecutionContext>() {
            @Override
            public Yaml visitDocument(Yaml.Document document, ExecutionContext executionContext) {
                Cursor c = getCursor();
                if (inPod(c) || inDeployment(c) || inStatefulSet(c) || inDaemonSet(c)) {
                    return document.withMarkers(document.getMarkers().addIfAbsent(result));
                } else {
                    return document;
                }
            }
        };
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        ContainerImage.ImageName imageToSearch = new ContainerImage.ImageName(repository, imageName, imageTag, "*");
        YamlSearchResult result = new YamlSearchResult(this, imageToSearch.toString());

        return new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Scalar visitScalar(Yaml.Scalar scalar, ExecutionContext ctx) {
                Cursor c = getCursor();
                if ((inContainerSpec(c) || (includeInitContainers && inInitContainerSpec(c))) && isImageName(c)) {
                    ContainerImage image = new ContainerImage(scalar.getValue());
                    if (image.getImageName().matches(imageToSearch)) {
                        return scalar.withMarkers(scalar.getMarkers().addIfAbsent(result));
                    }
                }
                return super.visitScalar(scalar, ctx);
            }
        };
    }

}
