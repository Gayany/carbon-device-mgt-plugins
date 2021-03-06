/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.device.mgt.iot.raspberrypi.service.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.analytics.dataservice.commons.SORT;
import org.wso2.carbon.analytics.dataservice.commons.SortByField;
import org.wso2.carbon.analytics.datasource.commons.exception.AnalyticsException;
import org.wso2.carbon.device.mgt.common.DeviceIdentifier;
import org.wso2.carbon.device.mgt.common.authorization.DeviceAccessAuthorizationException;
import org.wso2.carbon.device.mgt.iot.controlqueue.mqtt.MqttConfig;
import org.wso2.carbon.device.mgt.iot.raspberrypi.service.impl.dto.SensorRecord;
import org.wso2.carbon.device.mgt.iot.raspberrypi.service.impl.transport.RaspberryPiMQTTConnector;
import org.wso2.carbon.device.mgt.iot.raspberrypi.service.impl.util.APIUtil;
import org.wso2.carbon.device.mgt.iot.raspberrypi.plugin.constants.RaspberrypiConstants;
import org.wso2.carbon.device.mgt.iot.service.IoTServerStartupListener;
import org.wso2.carbon.device.mgt.iot.transport.TransportHandlerException;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class RaspberryPiControllerServiceImpl implements RaspberryPiControllerService {

    private static Log log = LogFactory.getLog(RaspberryPiControllerServiceImpl.class);
    private ConcurrentHashMap<String, String> deviceToIpMap = new ConcurrentHashMap<>();
    private RaspberryPiMQTTConnector raspberryPiMQTTConnector;

    @Path("device/{deviceId}/bulb")
    @POST
    public Response switchBulb(@PathParam("deviceId") String deviceId, @QueryParam("state") String state) {
        try {
            if (!APIUtil.getDeviceAccessAuthorizationService().isUserAuthorized(new DeviceIdentifier(deviceId,
                RaspberrypiConstants.DEVICE_TYPE))) {
                return Response.status(Response.Status.UNAUTHORIZED.getStatusCode()).build();
            }
            String switchToState = state.toUpperCase();
            if (!switchToState.equals(RaspberrypiConstants.STATE_ON) && !switchToState.equals(
                    RaspberrypiConstants.STATE_OFF)) {
                log.error("The requested state change shoud be either - 'ON' or 'OFF'");
                return Response.status(Response.Status.BAD_REQUEST.getStatusCode()).build();
            }
            String mqttResource = RaspberrypiConstants.BULB_CONTEXT.replace("/", "");
            raspberryPiMQTTConnector.publishDeviceData(deviceId, mqttResource, switchToState);
            return Response.ok().build();
        } catch (TransportHandlerException e) {
            log.error("Failed to send switch-bulb request to device [" + deviceId + "]");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        } catch (DeviceAccessAuthorizationException e) {
            log.error(e.getErrorMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Path("device/stats/{deviceId}")
    @GET
    @Consumes("application/json")
    @Produces("application/json")
    public Response getRaspberryPiTemperatureStats(@PathParam("deviceId") String deviceId,
                                                   @QueryParam("from") long from, @QueryParam("to") long to) {
        String fromDate = String.valueOf(from);
        String toDate = String.valueOf(to);
        String query = "deviceId:" + deviceId + " AND deviceType:" +
                RaspberrypiConstants.DEVICE_TYPE + " AND time : [" + fromDate + " TO " + toDate + "]";
        String sensorTableName = RaspberrypiConstants.TEMPERATURE_EVENT_TABLE;
        try {
            if (!APIUtil.getDeviceAccessAuthorizationService().isUserAuthorized(new DeviceIdentifier(deviceId,
                    RaspberrypiConstants.DEVICE_TYPE))) {
                return Response.status(Response.Status.UNAUTHORIZED.getStatusCode()).build();
            }
            List<SortByField> sortByFields = new ArrayList<>();
            SortByField sortByField = new SortByField("time", SORT.ASC, false);
            sortByFields.add(sortByField);
            List<SensorRecord> sensorRecords = APIUtil.getAllEventsForDevice(sensorTableName, query, sortByFields);
            return Response.status(Response.Status.OK.getStatusCode()).entity(sensorRecords).build();
        } catch (AnalyticsException e) {
            String errorMsg = "Error on retrieving stats on table " + sensorTableName + " with query " + query;
            log.error(errorMsg);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()).entity(errorMsg).build();
        } catch (DeviceAccessAuthorizationException e) {
            log.error(e.getErrorMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    private boolean waitForServerStartup() {
        while (!IoTServerStartupListener.isServerReady()) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                return true;
            }
        }
        return false;
    }

    public RaspberryPiMQTTConnector getRaspberryPiMQTTConnector() {
        return raspberryPiMQTTConnector;
    }

    public void setRaspberryPiMQTTConnector(
            final RaspberryPiMQTTConnector raspberryPiMQTTConnector) {
        Runnable connector = new Runnable() {
            public void run() {
                if (waitForServerStartup()) {
                    return;
                }
                //The delay is added for the server to starts up.
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                RaspberryPiControllerServiceImpl.this.raspberryPiMQTTConnector = raspberryPiMQTTConnector;
                if (MqttConfig.getInstance().isEnabled()) {
                    raspberryPiMQTTConnector.connect();
                } else {
                    log.warn("MQTT disabled in 'devicemgt-config.xml'. Hence, RaspberryPiMQTTConnector not started.");
                }
            }
        };
        Thread connectorThread = new Thread(connector);
        connectorThread.start();
    }

}
