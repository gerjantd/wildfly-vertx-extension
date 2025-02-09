/*
 * Copyright (C) 2022 RedHat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.extension.vertx.deployment.processors;

import io.vertx.core.json.JsonObject;
import org.jboss.as.ee.structure.DeploymentType;
import org.jboss.as.ee.structure.DeploymentTypeMarker;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.Phase;
import org.jboss.as.server.deployment.annotation.CompositeIndex;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.vfs.VirtualFile;
import org.wildfly.extension.vertx.deployment.VertxDeploymentAttachment;
import org.wildfly.extension.vertx.deployment.VerticleDeploymentsMetaData;
import org.wildfly.extension.vertx.logging.VertxLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.jboss.as.server.deployment.Attachments.*;

/**
 *
 * Processor used to parse verticle deployments configuration from archive.
 *
 * @author <a href="aoingl@gmail.com">Lin Gao</a>
 */
public class VerticleDeploymentMarkerProcessor implements DeploymentUnitProcessor {

    private static final String WEB_INF_VERTX_DEPLOYMENT = "WEB-INF/vertx-deployment.json";
    private static final String META_INF_VERTX_DEPLOYMENT = "META-INF/vertx-deployment.json";

    public static final Phase PHASE = Phase.PARSE;
    public static final int PRIORITY = 0x4000;

    private static final String VERTX_ANNOTATION_NAME = "io.vertx.core.Vertx";
    private static final String VERTX_RX2_ANNOTATION_NAME = "io.vertx.reactivex.core.Vertx";
    private static final String VERTX_RX3_ANNOTATION_NAME = "io.vertx.rxjava3.core.Vertx";

    private static final List<DotName> dotNames = new ArrayList<>();
    static {
        dotNames.add(DotName.createSimple("jakarta.inject.Inject"));
        dotNames.add(DotName.createSimple("jakarta.annotation.Resource"));
    }

    @Override
    public void deploy(DeploymentPhaseContext context) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = context.getDeploymentUnit();
        ResourceRoot deploymentRoot = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT);
        if (deploymentRoot == null) {
            return;
        }
        VirtualFile vertxDeploymentFile;
        if (DeploymentTypeMarker.isType(DeploymentType.WAR, deploymentUnit)) {
            vertxDeploymentFile = deploymentRoot.getRoot().getChild(WEB_INF_VERTX_DEPLOYMENT);
        } else {
            vertxDeploymentFile = deploymentRoot.getRoot().getChild(META_INF_VERTX_DEPLOYMENT);
        }
        final boolean vertxDeploymentExists = vertxDeploymentFile != null && vertxDeploymentFile.exists() && vertxDeploymentFile.isFile();
        if (vertxDeploymentExists) {
            attachVertxDeployments(deploymentUnit, vertxDeploymentFile);
        } else {
            // check '@Injection Vertx' or '@Resource Vertx' in case of no vertx-deployment.json file found
            final CompositeIndex index = deploymentUnit.getAttachment(COMPOSITE_ANNOTATION_INDEX);
            if (dotNames.stream().anyMatch(dotName -> annotated(index, dotName))) {
                VertxDeploymentAttachment.attachVertxDeployments(deploymentUnit, new VerticleDeploymentsMetaData(new JsonObject()));
            }
        }
    }

    private boolean annotated(CompositeIndex index, DotName injectAnnotationName) {
        final List<AnnotationInstance> resourceAnnotations = index.getAnnotations(injectAnnotationName);
        for (AnnotationInstance annotation : resourceAnnotations) {
            final AnnotationTarget annotationTarget = annotation.target();
            if (annotationTarget instanceof FieldInfo) {
                final String fieldType = annotationTarget.asField().type().name().toString();
                if (fieldType.equals(VERTX_ANNOTATION_NAME) || fieldType.equals(VERTX_RX2_ANNOTATION_NAME)
                  || fieldType.equals(VERTX_RX3_ANNOTATION_NAME)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void attachVertxDeployments(DeploymentUnit deploymentUnit, VirtualFile vertxDeploymentFile) throws DeploymentUnitProcessingException {
        try {
            String jsonContent = streamToString(vertxDeploymentFile);
            if (jsonContent.trim().isEmpty()) {
                jsonContent = "{}";
            }
            VertxDeploymentAttachment.attachVertxDeployments(deploymentUnit, new VerticleDeploymentsMetaData(new JsonObject(jsonContent)));
        } catch (IOException ioe) {
            throw VertxLogger.VERTX_LOGGER.failedToReadConfig(vertxDeploymentFile.getName(), ioe);
        }
    }

    private String streamToString(VirtualFile vertxDeploymentFile) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (InputStream input = vertxDeploymentFile.openStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))
        ) {
            String line;
            while((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    @Override
    public void undeploy(DeploymentUnit deploymentUnit) {
        VertxDeploymentAttachment.removeVertxDeploymentsMeta(deploymentUnit);
    }

}
