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

'use strict';

define(function (require) {
    var modules = require('modules');


    modules.get('a4c-plugin-sample', ['ngResource']).factory('soeService', ['$resource',
        function($resource) {

            var clRequestDoc=$resource('rest/cloudlightning/requestdocument',{},{

                'prepare':{

                    method: 'POST',
                    isArray: false,
                    headers: {
                        'Content-Type': 'application/json; charset=UTF-8'
                    }
                }

            });

            var clResponseDoc=$resource('rest/soe/optimize',{},{

                    'receive':{
                        method: 'POST',
                        isArray: false,
                        headers: {
                            'Content-Type': 'application/json; charset=UTF-8'
                        }
                    }
                }

            );

            var optimizationStatus=$resource('rest/soe/optimize/:appName/:appVersion',{},{

                'receive':{
                    method: 'GET',
                    isArray: false,
                    headers: {
                        'Content-Type': 'application/json; charset=UTF-8'
                    }
                },

                'delete':{
                    method: 'DELETE',
                    isArray: false,
                    headers: {
                        'Content-Type': 'application/json; charset=UTF-8'
                    }
                }
            });


            var clComponents=$resource('rest/components/search',{},{
                'getComponents':{
                    method:'POST',
                    isArray:false,
                    headers:{
                        'Content-Type':'application/json; charset=UTF-8'
                    }
                }

            });

            var clReplaceComponent=$resource('rest/latest/editor/:topologyId/execute',{},{
                'replace':{
                    method: 'POST',
                    isArray:false,
                    headers: {
                        'Content-Type': 'application/json; charset=UTF-8'
                    }

                }
            });

            var saveModifications=$resource('rest/latest/editor/:topologyId',{},{
                'create':{
                    method: 'POST',
                    isArray:false,
                    headers: {
                        'Content-Type': 'application/json; charset=UTF-8'
                    }

                }
            });

            return {
                'cloudlightningRequestDocument': clRequestDoc,
                'cloudlightningResponseDocument': clResponseDoc,
                'cloudlightningComponents':clComponents,
                'cloudlightningReplaceComponent':clReplaceComponent,
                'saveModifications':saveModifications,
                'optimizationStatus':optimizationStatus
            };
        }
    ]);

    modules.get('a4c-plugin-sample', ['a4c-topology-editor', 'a4c-tosca']).factory('topologyEditor', ['topologyServices','soeService',
        function(topologyServices, soeService) {
            
            var ClTopologyEditorMixin = function(scope) {
                this.scope = scope;
            };
            ClTopologyEditorMixin.prototype = {
                constructor: ClTopologyEditorMixin,



                editClTopo: function(clRequest,clResponse,nodeTemplatesList,i,j,prevOpId, _callback){
                    // console.log("new update");

                    var topoId = this.scope.topologyId;
                    if(i<clRequest.length) {
                       // this.scope.clTopologyStatus="IN_PROGRESS";
                        if (j < clResponse.length) {

                            // console.log("i=" + i + "j=" + j);

                            var nodeIdReq = clRequest[i].name;
                            var nodeIdRes = clResponse[j].id;

                            

                            if (nodeIdReq === nodeIdRes) {

                                var newNodeType = clResponse[j].type;
                                var oldNodeType = clRequest[i].type;
                                if(oldNodeType !== newNodeType.split(":")[0] && oldNodeType !== newNodeType){
                                    //In this case we do not need to change this service
                                

                                    // console.log("Change " + nodeIdReq + " from " + oldNodeType + " to " + newNodeType);


                                    
                                    // console.log(nodeTemplatesList);
                                   // var newNodeTemplName = this.generateClNodeTemplateName(newNodeType, nodeTemplatesList);

                                    //console.log("new node name=" + newNodeTemplName);



                                    var nodeTemplateRequest = {
                                        type: "org.alien4cloud.tosca.editor.operations.nodetemplate.ReplaceNodeOperation",
                                        nodeName: nodeIdReq,
                                        newTypeId: clResponse[j].type,
                                        previousOperationId: prevOpId
                                    };


                                    var instance = this;
                                    soeService.cloudlightningReplaceComponent.replace({
                                        topologyId: topoId,

                                    }, angular.toJson(nodeTemplateRequest),

                                        function(result) {
                                            // console.log("aici1");

                                            topologyServices.dao.get({
                                                topologyId: topoId
                                            }, function (successResult) {
                                                // var nodeTemplatesList=instance.getNodeTemplatesList(successResult.data);
                                            // var newNodeTemplName = instance.generateClNodeTemplateName(newNodeType, nodeTemplatesList);

                                            // console.log("new node name=" + newNodeTemplName);
                                            var nodeRenameRequest = {
                                                type: "org.alien4cloud.tosca.editor.operations.nodetemplate.RebuildNodeOperation",
                                                nodeName: nodeIdReq,
                                                previousOperationId: result.data.operations[result.data.operations.length - 1].id
                                            };

                                            soeService.cloudlightningReplaceComponent.replace({
                                                    topologyId: topoId,

                                                }, angular.toJson(nodeRenameRequest),

                                                function (result) {

                                                    instance.scope.oldNodes.push(nodeIdReq);
                                                    instance.scope.newNodes.push(newNodeType);
                                                    instance.scope.replacements.push({
                                                        msg: 'Replaced ' + nodeIdReq,
                                                        action1: oldNodeType,
                                                        action2: newNodeType
                                                    });
                                                     
                                                    instance.editClTopo(clRequest, clResponse, nodeTemplatesList, i, j + 1, result.data.operations[result.data.operations.length - 1].id, _callback);
                                                });
                                            });
                                                }, function (errorResult) {
                                                    instance.scope.clTopologyStatus = "ERROR";
                                                    instance.scope.clPrintingStatus = "ERROR";

                                                }
                                            );
                                }else{
                                    this.scope.replacements.push({
                                                        msg: 'Keeping ' + nodeIdReq,
                                                        action1: oldNodeType,
                                                        action2: newNodeType
                                                    });
                                    this.editClTopo(clRequest, clResponse, nodeTemplatesList, i+1, 0, prevOpId, _callback);

                                }

                            }else{
                                this.editClTopo(clRequest, clResponse, nodeTemplatesList, i, j+1, prevOpId, _callback);
                            }

                        } else {
                            j = 0;
                            i++;
                            this.editClTopo(clRequest, clResponse, nodeTemplatesList, i, j,prevOpId, _callback);

                        }
                    }else{
                        
                        var instance = this;
                        if(this.scope.replacements.length<=0){
                            this.scope.replacements.push({msg:'No Cloudlightning optimization was performed',action1:'No changes',action2:''});
                            this.scope.clTopologyStatus="ERROR";
                            this.scope.clPrintingStatus="System unable to find resources...";

                        }else{
                            if(this.scope.clTopologyStatus!=="OPTIMISED"){
                                this.scope.clTopologyStatus="OPTIMISED";
                                this.scope.clPrintingStatus="OPTIMISED";
                            }else{
                                //In this case we are switching from an optimized topology back to the abstract one
                                this.scope.clTopologyStatus="NOT_STARTED";
                                this.scope.clPrintingStatus="NOT STARTED";
                            }
                            
                        }
                        
                        this.scope.finishedChanges=true;
                        if(_callback){
                            _callback(prevOpId);
                        }
                    }

                },

                applyAdditionalRelationships: function(operations, index, prevOpId, autodeploy, appName, appEnvId){
                    var instance = this;
                    var topoId = this.scope.topologyId;

                    if(index < operations.length){
                        var op = operations[index];
                        op["previousOperationId"] = prevOpId;
                        soeService.cloudlightningReplaceComponent.replace({
                                                topologyId: topoId,

                                            }, angular.toJson(op),

                                            function (result) {

                                                var a = op.type.split(".");
                                                instance.scope.replacements.push({
                                                    msg: a[a.length - 1] + ' ' + op.nodeName,
                                                    action1: 'Success',
                                                    action2: ''
                                                });
                                                 
                                                instance.applyAdditionalRelationships(operations, index + 1, result.data.operations[result.data.operations.length - 1].id, autodeploy, appName, appEnvId);
                                            });
                                        
                    }else{

                        
                        // if(this.scope.clTopologyStatus!="OPTIMISED"){
                        //     this.scope.clTopologyStatus="OPTIMISED";
                        //     this.scope.clPrintingStatus="OPTIMISED";
                        // }

                        if(prevOpId != null){
                            soeService.saveModifications.create({
                              topologyId: topoId,
                              lastOperationId: prevOpId
                            }, null, function(result) {
                              if(_.undefined(result.error)) {
                                instance.scope.replacements.push({msg:'All operations completed',action1:'Success',action2:''});
                                if(autodeploy){
                                
                                    instance.deploy(appName, appEnvId);
                                                                          
                                }
                              }
                            });                  
                        }      

                    }

                },


                deleteNodes: function(nodes, index, prevOpId, autodeploy, appName, appEnvId){
                    var instance = this;
                    var topoId = this.scope.topologyId;

                    if(index < nodes.length){
                        var op = {};
                        op["type"] = "org.alien4cloud.tosca.editor.operations.nodetemplate.DeleteNodeOperation";
                        op["previousOperationId"] = prevOpId;
                        op["nodeName"] = nodes[index];
                        soeService.cloudlightningReplaceComponent.replace({
                                                topologyId: topoId,

                                            }, angular.toJson(op),

                                            function (result) {

                                                instance.scope.replacements.push({
                                                    msg: 'Deleted ' + nodes[index],
                                                    action1: 'Success',
                                                    action2: ''
                                                });
                                                 
                                                instance.deleteNodes(nodes, index + 1, result.data.operations[result.data.operations.length - 1].id, autodeploy, appName, appEnvId);
                                            });
                                        
                    }else{

                        
                        if(prevOpId != null){
                            soeService.saveModifications.create({
                              topologyId: topoId,
                              lastOperationId: prevOpId
                            }, null, function(result) {
                              if(_.undefined(result.error)) {
                                instance.scope.replacements.push({msg:'All operations completed',action1:'Success',action2:''});
                                if(autodeploy){
                                    
                                    instance.undeploy(appName, appEnvId);
                                    
                                }
                              }
                            });  
                        }                      

                    }

                },

                deploy: function(appId, envId) {
                // Application details with deployment properties
                  var deployApplicationRequest = {
                    applicationId: appId,
                    applicationEnvironmentId: envId
                  };
                  $scope.isDeploying = true;
                  topologyServices.deployApplication.deploy([], angular.toJson(deployApplicationRequest), function() {
                    $scope.deploymentContext.selectedEnvironment.status = 'INIT_DEPLOYMENT';
                    $scope.isDeploying = false;
                  }, function() {
                    $scope.isDeploying = false;
                  });
                },

                undeploy: function(appId, envId) {
                // Application details with deployment properties
                  $scope.isUnDeploying = true;
                  applicationServices.deployment.undeploy({
                    applicationId: $scope.application.id,
                    applicationEnvironmentId: $scope.deploymentContext.selectedEnvironment.id
                  }, function() {
                    $scope.deploymentContext.selectedEnvironment.status = 'UNDEPLOYMENT_IN_PROGRESS';
                    $scope.isUnDeploying = false;
                    $scope.stopEvent();
                  }, function() {
                    $scope.isUnDeploying = false;
                  });
                },


                rebuildRelationships: function (topologyJson, prevOpId) {
                    var rels = this.getRelationships(topologyJson);
                    // console.log(rels.keys());
                    var topoId = this.scope.topologyId;
                    // console.log("Rebuilding relationships");
                    for(var key of rels.keys()){
                        for(var i = 0 ; i < rels.get(key).length; i++){
                            // console.log("N = [" + key + "]; R = [" + rels.get(key)[i] + "]");
                            var nodeRenameRequest = {
                                type: "org.alien4cloud.tosca.editor.operations.relationshiptemplate.RebuildRelationshipOperation",
                                nodeName: key,
                                relationshipName: rels.get(key)[i],
                                previousOperationId: prevOpId
                            };

                            soeService.cloudlightningReplaceComponent.replace({
                                    topologyId: topoId,

                                }, angular.toJson(nodeRenameRequest),

                                function (result) {
                                    console.log(result);
                                }, function (errorResult){
                                    console.log(errorResult);
                                });
                        }

                    }

                },


                getRelationships: function (topologyJson){
                    var nodeRelationMap = new Map();

                    if(topologyJson.topology!=undefined) {

                        var topology = topologyJson.topology;

                        if (topology.nodeTemplates != undefined) {

                            var nodeTemplates = topology.nodeTemplates;
                            for (var key in nodeTemplates) {

                                var isAbstractNode = false;


                                var nodeName = key;
                                var nodeValue = nodeTemplates[key];

                                if(nodeValue.relationships != undefined){
                                    var rels = new Array();

                                    for(var i = 0; i < nodeValue.relationships.length; i++){
                                        if(nodeValue.relationships[i].key != undefined){
                                            rels.push(nodeValue.relationships[i].key);
                                        }
                                    }
                                    nodeRelationMap.set(nodeName, rels);
                                }
                            }
                        }
                    }
                    return nodeRelationMap;
                },

                getCapabilities: function (topologyJson){
                    var capaMap = new Map();

                    if(topologyJson.topology!=undefined) {

                        var topology = topologyJson.topology;

                        if (topology.nodeTemplates != undefined) {

                            var nodeTemplates = topology.nodeTemplates;
                            for (var key in nodeTemplates) {

                                var isAbstractNode = false;


                                var nodeName = key;
                                var nodeValue = nodeTemplates[key];

                                if(nodeValue.relationships != undefined){
                                    var rels = new Array();

                                    for(var i = 0; i < nodeValue.relationships.length; i++){
                                        if(nodeValue.relationships[i].key != undefined){
                                            rels.push(nodeValue.relationships[i].key);
                                        }
                                    }
                                    capaMap.set(nodeName, rels);
                                }
                            }
                        }
                    }
                    return capaMap;
                },

                generateClNodeTemplateName: function(newNodeType,nodeTemplatesList){

                    var baseName = this.simpleName(newNodeType);
                    var i = 1;
                    var tempName = baseName;

                    for(var j=0;j<nodeTemplatesList.length;j++){
                        var node=nodeTemplatesList[j];
                        if(node.id==tempName){
                            i++;
                            tempName = baseName + '_' + i;
                        }
                    }

                    return tempName;


                },

                getNodeTemplatesList: function(topologyJson){
                    var nodeTemplatesList=new Array();


                    // console.log("Topology in getNodeTemplatelist:"+JSON.stringify(topologyJson));

                    if(topologyJson.topology!=undefined) {

                        var topology = topologyJson.topology;

                        if (topology.nodeTemplates != undefined) {

                            var nodeTemplates = topology.nodeTemplates;
                            for (var key in nodeTemplates) {

                                var nodeName = key;
                                var nodeValue = nodeTemplates[key];

                                var clNode = new Object();
                                clNode.id = nodeName;

                                if (nodeValue.type != undefined) {
                                    clNode.type = nodeValue.type;
                                }

                                nodeTemplatesList.push(clNode);
                            }
                        }
                    }
                    return nodeTemplatesList;
                },

                simpleName: function(longName){

                    var parts = $.trim(longName).split(':');
                    var tokens=$.trim(parts[0]).split('.');
                    if (tokens.length > 0) {
                        return tokens[tokens.length - 1];
                    } else {
                        return longName;
                    }
                }
                
               
            };
            return function(scope) {
                var instance = new ClTopologyEditorMixin(scope);
                scope.clTopo = instance;

            };
            }
    ]); // modules
}); // define