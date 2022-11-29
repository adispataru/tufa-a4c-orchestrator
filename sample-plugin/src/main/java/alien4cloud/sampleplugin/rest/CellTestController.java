/*
 * Copyright 2017 Institute e-Austria Timisoara.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package alien4cloud.sampleplugin.rest;

import alien4cloud.sampleplugin.services.A4CRestTemplateFactory;
import alien4cloud.sampleplugin.services.BlueprintScheduler;
import alien4cloud.sampleplugin.services.RestTemplateFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.annotation.Resource;
import java.util.Map;

/**
 * Created by adrian on 09.03.2017.
 */
@Controller()
public class CellTestController {

    private static final Logger log = LoggerFactory.getLogger(CellTestController.class);

    @Resource
    RestTemplateFactory restTemplateFactory;

    @Resource
    A4CRestTemplateFactory a4crestTemplateFactory;

    @Resource
    BlueprintScheduler blueprintScheduler;

    @RequestMapping(value = "/test/resource_template", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> processSOSMResult(@RequestBody Map<String, String> blueprint){
        return new ResponseEntity<String>(HttpStatus.OK);
    }

//    @RequestMapping(value = "/test/resource_decommission", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
//    public ResponseEntity<String> processSOSMResult(@RequestBody BlueprintResourcedTemplate bp){
//
//        return new ResponseEntity<String>(HttpStatus.OK);
//    }


}
