/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.kubernetes.search;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.kubernetes.ContainerImage;
import org.openrewrite.yaml.XPathMatcher;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.search.YamlSearchResult;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Set;

@Value
@EqualsAndHashCode(callSuper = true)
public class FindDisallowedImageTags extends Recipe {

    @Option(displayName = "Disallowed tags",
            description = "The set of image tags to find which are considered disallowed.",
            example = "latest")
    Set<String> disallowedTags;

    @Override
    public String getDisplayName() {
        return "Disallowed tags";
    }

    @Override
    public String getDescription() {
        return "The set of image tags to find which are considered disallowed.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        XPathMatcher imageMatcher = new XPathMatcher("//spec/containers/image");
        XPathMatcher initImageMatcher = new XPathMatcher("//spec/initContainers/image");

        return new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Scalar visitScalar(Yaml.Scalar scalar, ExecutionContext executionContext) {
                Cursor parent = getCursor().getParentOrThrow();
                if (imageMatcher.matches(parent) || initImageMatcher.matches(parent)) {
                    ContainerImage image = new ContainerImage(scalar);
                    if (disallowedTags.stream().anyMatch(t -> t.equals(image.getImageName().getTag()))) {
                        return scalar.withMarkers(scalar.getMarkers().addIfAbsent(new YamlSearchResult(
                                FindDisallowedImageTags.this,
                                "disallowed tag: " + disallowedTags
                        )));
                    }
                }
                return super.visitScalar(scalar, executionContext);
            }
        };
    }

}
