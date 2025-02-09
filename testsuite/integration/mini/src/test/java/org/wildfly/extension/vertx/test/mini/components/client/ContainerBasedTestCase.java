/*
 *  Copyright (c) 2022 The original author or authors
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of Apache License v2.0 which
 *  accompanies this distribution.
 *
 *       The Apache License v2.0 is available at
 *       http://www.opensource.org/licenses/apache2.0.php
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package org.wildfly.extension.vertx.test.mini.components.client;

import static org.junit.Assume.assumeTrue;

import org.junit.BeforeClass;
import org.testcontainers.DockerClientFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Container based tests.
 *
 * @author <a href="mailto:aoingl@gmail.com">Lin Gao</a>
 */
public abstract class ContainerBasedTestCase {

    @BeforeClass
    public static void checkDockerEnv() {
        assumeTrue("Docker isn't available, test is skipped.", isContainerOK());
    }

    public static boolean isContainerOK() {
        if (Boolean.getBoolean("container.tests.mandatory")) {
            return true;
        }
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Exception e) {
            //ignored
            return false;
        }
    }

    protected abstract String getContainerConnStr();

    protected String getServiceConnStrEncoded() {
        return URLEncoder.encode(getContainerConnStr(), StandardCharsets.UTF_8);
    }

}
