/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.device.mgt.iot.raspberrypi.service.impl.transport;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.wso2.carbon.apimgt.application.extension.APIManagementProviderService;
import org.wso2.carbon.apimgt.application.extension.dto.ApiApplicationKey;
import org.wso2.carbon.apimgt.application.extension.exception.APIManagerException;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.device.mgt.iot.controlqueue.mqtt.MqttConfig;
import org.wso2.carbon.device.mgt.iot.raspberrypi.service.impl.util.APIUtil;
import org.wso2.carbon.device.mgt.iot.raspberrypi.plugin.constants.RaspberrypiConstants;
import org.wso2.carbon.device.mgt.iot.transport.TransportHandlerException;
import org.wso2.carbon.device.mgt.iot.transport.mqtt.MQTTTransportHandler;
import org.wso2.carbon.identity.jwt.client.extension.JWTClient;
import org.wso2.carbon.identity.jwt.client.extension.dto.AccessTokenInfo;
import org.wso2.carbon.identity.jwt.client.extension.exception.JWTClientException;
import org.wso2.carbon.user.api.UserStoreException;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class RaspberryPiMQTTConnector extends MQTTTransportHandler {
    private static Log log = LogFactory.getLog(RaspberryPiMQTTConnector.class);
    private static final String subscribeTopic = "wso2/" + RaspberrypiConstants.DEVICE_TYPE + "/+/publisher";
    private static final String KEY_TYPE = "PRODUCTION";
    private static final String EMPTY_STRING = "";

    private static final String iotServerSubscriber = UUID.randomUUID().toString().substring(0, 5);

    private RaspberryPiMQTTConnector() {
        super(iotServerSubscriber, RaspberrypiConstants.DEVICE_TYPE,
              MqttConfig.getInstance().getMqttQueueEndpoint(), subscribeTopic);
    }

    @Override
    public void connect() {
        Runnable connector = new Runnable() {
            public void run() {
                while (!isConnected()) {
					PrivilegedCarbonContext.startTenantFlow();
					PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(
							RaspberrypiConstants.DEVICE_TYPE_PROVIDER_DOMAIN, true);
					try {
						String applicationUsername = PrivilegedCarbonContext.getThreadLocalCarbonContext().getUserRealm()
										.getRealmConfiguration().getAdminUserName();
                        PrivilegedCarbonContext.getThreadLocalCarbonContext().setUsername(applicationUsername);
                        APIManagementProviderService apiManagementProviderService = APIUtil.getAPIManagementProviderService();
						String[] tags = {RaspberrypiConstants.DEVICE_TYPE};
						ApiApplicationKey apiApplicationKey = apiManagementProviderService.generateAndRetrieveApplicationKeys(
										RaspberrypiConstants.DEVICE_TYPE, tags, KEY_TYPE, applicationUsername, true);
						JWTClient jwtClient = APIUtil.getJWTClientManagerService().getJWTClient();
						String scopes = "device_type_" + RaspberrypiConstants.DEVICE_TYPE + " device_mqtt_connector";
						AccessTokenInfo accessTokenInfo = jwtClient.getAccessToken(apiApplicationKey.getConsumerKey(),
						    apiApplicationKey.getConsumerSecret(), applicationUsername, scopes);
                        //create token
                        String accessToken = accessTokenInfo.getAccessToken();
                        setUsernameAndPassword(accessToken, EMPTY_STRING);
                        connectToQueue();
					} catch (TransportHandlerException e) {
						log.error("Connection/Subscription to MQTT Broker at: " + mqttBrokerEndPoint + " failed", e);
						try {
							Thread.sleep(timeoutInterval);
						} catch (InterruptedException ex) {
							log.error("MQTT-Connector: Thread Sleep Interrupt Exception.", ex);
						}
					} catch (JWTClientException e) {
						log.error("Failed to retrieve token from JWT Client.", e);
                        return;
					} catch (UserStoreException e) {
						log.error("Failed to retrieve the user.", e);
                        return;
					} catch (APIManagerException e) {
						log.error("Failed to create an application and generate keys.", e);
                        return;
					} finally {
						PrivilegedCarbonContext.endTenantFlow();
					}
                }
            }
        };

        Thread connectorThread = new Thread(connector);
        connectorThread.start();
    }

    @Override
    public void processIncomingMessage(MqttMessage message, String... messageParams) throws TransportHandlerException {
    }

    @Override
    public void disconnect() {
        Runnable stopConnection = new Runnable() {
            public void run() {
                while (isConnected()) {
                    try {
                        closeConnection();
                    } catch (MqttException e) {
                        if (log.isDebugEnabled()) {
                            log.warn("Unable to 'STOP' MQTT connection at broker at: " + mqttBrokerEndPoint
                                             + " for device-type - " + RaspberrypiConstants.DEVICE_TYPE, e);
                        }

                        try {
                            Thread.sleep(timeoutInterval);
                        } catch (InterruptedException e1) {
                            log.error("MQTT-Terminator: Thread Sleep Interrupt Exception at device-type - " +
                                              RaspberrypiConstants.DEVICE_TYPE, e1);
                        }
                    }
                }
            }
        };

        Thread terminatorThread = new Thread(stopConnection);
        terminatorThread.start();
    }

    @Override
    public void processIncomingMessage() throws TransportHandlerException {

    }

    @Override
    public void processIncomingMessage(MqttMessage message) throws TransportHandlerException {

    }

    @Override
    public void publishDeviceData() throws TransportHandlerException {

    }

    @Override
    public void publishDeviceData(MqttMessage publishData) throws TransportHandlerException {

    }

    @Override
    public void publishDeviceData(String... publishData) throws TransportHandlerException {
        if (publishData.length != 3) {
            String errorMsg = "Incorrect number of arguments received to SEND-MQTT Message. " +
                    "Need to be [owner, deviceId, resource{BULB/TEMP}, state{ON/OFF or null}]";
            log.error(errorMsg);
            throw new TransportHandlerException(errorMsg);
        }

        String deviceId = publishData[0];
        String resource = publishData[1];
        String state = publishData[2];

        MqttMessage pushMessage = new MqttMessage();
        String publishTopic = "wso2/" + APIUtil.getTenantDomainOftheUser() + "/"
                + RaspberrypiConstants.DEVICE_TYPE + "/" + deviceId;
        String actualMessage = resource + ":" + state;
        pushMessage.setPayload(actualMessage.getBytes(StandardCharsets.UTF_8));
        pushMessage.setQos(DEFAULT_MQTT_QUALITY_OF_SERVICE);
        pushMessage.setRetained(false);
        publishToQueue(publishTopic, pushMessage);
    }
}

