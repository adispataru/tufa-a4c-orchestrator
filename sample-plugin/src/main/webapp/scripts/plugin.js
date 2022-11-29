// This is the ui entry point for the plugin
function buildQuery(filters){

  var query=new Object();

  //add empty query field
  query["query"]="";

  //add from and size fields
  query["from"]=0;
  query["size"]=200;

  //add type fields
  query["type"]="NODE_TYPE";

  //add filters


  query["filters"]=filters;

  return query;


}

function satisfies(performance, performanceString){
  var mapping = {"Low": 0.99, "Medium": 1.5, "Premium": 3}
  console.log(performance + " ? " + performanceString);
  return performance > mapping[performanceString];
}

function prepareClRequest(topologyJson, performanceString){

  var clFullRequest = {};
  var clRequest=new Array();


  if(topologyJson.topology!=undefined){

    var topology=topologyJson.topology;

    if(topology.nodeTemplates!=undefined){

      var nodeTemplates=topology.nodeTemplates;
      clFullRequest["name"] = topology.archiveName;
      clFullRequest["version"] = topology.archiveVersion;
      for (var key in nodeTemplates){

        var isAbstractNode=false;


        var nodeName = key;
        var nodeValue = nodeTemplates[key];


        var clNode=new Object();
        clNode.name=nodeName;


        if(nodeValue.type!=undefined) {
          clNode.type = nodeValue.type;
          var capability=null;

          if (topologyJson.nodeTypes!=undefined && topologyJson.nodeTypes[clNode.type]!=undefined && topologyJson.nodeTypes[clNode.type].abstract!=undefined && topologyJson.nodeTypes[clNode.type].abstract==true )
          {

            isAbstractNode=true;


            console.log(nodeName+" is abstract");
            console.log(nodeValue);


            //get service capability
            if(nodeValue.capabilities!=undefined){
              for(var i = 0; i < nodeValue.capabilities.length; i++) {
                if (nodeValue.capabilities[i].key != undefined && nodeValue.capabilities[i].key != null &&  nodeValue.capabilities[i].key==="service" ) {
                  if(nodeValue.capabilities[i].value.type != undefined && nodeValue.capabilities[i].value.type != null){

                    capability=nodeValue.capabilities[i].value.type;
                    break;
                  }
                }
              }
            }


            //get non-abstract nodes with the same capability

            //build query using current node type
            console.log("Searching for nodes with capability: " + capability)
            if(capability!=null) {
              var filters = {"abstract": [false], "capabilities.type": [capability]}
              var queryForComponents = buildQuery(filters);

              console.log("querying...")
              console.log("Query for component "+queryForComponents);

              $.ajax({
                url: 'rest/latest/components/search',
                type: 'post',
                async: false,
                dataType: 'json',
                contentType: "application/json",
                success: function (result) {
                  console.log("Received data from component search:"+result.data.data);
                  var impList=new Array();
                  for(var i=0;i<result.data.data.length;i++){
                    var service = result.data.data[i];
                    //check if service satisfies performance
                    var performance = 0.0;
                    for(var j=0; j < service.tags.length; j++){
                      if(service.tags[j].name === "performance"){
                        performance = service.tags[j].value;
                        console.log("performance found " + performance);
                      }
                    }

                    if(satisfies(performance, performanceString)){
                      var imp=new Object();
                      imp['elementId']=service.elementId;
                      imp['id']=service.id;
                      imp['archiveVersion']=service.archiveVersion;
                      impList.push(imp);
                    }else{
                      console.log("Filtering out " + service.elementId + " because performance " + performance + " < " + performanceString);
                    }
                  }
                  if(impList.length < 1){
                    //if no service satisfies the performance, then add all of them and let SOSM choose
                    console.log("No service was satisfying performance constraint. Adding all and letting SOSM choose");
                    for(var i=0;i<result.data.data.length;i++){
                      var service = result.data.data[i];
                      var imp=new Object();
                      imp['elementId']=service.elementId;
                      imp['id']=service.id;
                      imp['archiveVersion']=service.archiveVersion;
                      impList.push(imp);
                    }
                  }
                  clNode.implementationList=impList;

                },
                data: JSON.stringify(queryForComponents)
              });
            }

          }else{
            if(nodeValue.capabilities!=undefined){
              for(var i = 0; i < nodeValue.capabilities.length; i++) {
                if (nodeValue.capabilities[i].key != undefined && nodeValue.capabilities[i].key != null &&  nodeValue.capabilities[i].key==="service" ) {
                  if(nodeValue.capabilities[i].value.type != undefined && nodeValue.capabilities[i].value.type != null){

                    capability=nodeValue.capabilities[i].value.type;
                    break;
                  }
                }
              }


            }
          }

          if (topologyJson.nodeTypes!=undefined && topologyJson.nodeTypes[clNode.type]!=undefined && topologyJson.nodeTypes[clNode.type].id!=undefined )
          {
            clNode.id=topologyJson.nodeTypes[clNode.type].id;
          }

        }


        if(isAbstractNode)
          clRequest.push(clNode);
        else if(capability != null){
          var impList=new Array();
          var imp=new Object();
          imp['elementId']=topologyJson.nodeTypes[clNode.type].elementId;
          imp['id']=topologyJson.nodeTypes[clNode.type].id;
          imp['archiveVersion']=topologyJson.nodeTypes[clNode.type].archiveVersion;
          impList.push(imp);
          clNode.implementationList = impList;
          clRequest.push(clNode);

        }
      }
    }
  }
  clFullRequest["services"] = clRequest;
  return clFullRequest;

}
function getNodeTemplatesList(topologyJson){
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
}

/* global define */

define(function (require) {
  'use strict';
  
  var states = require('states');
  var modules = require('modules');
  var prefixer = require('scripts/plugin-url-prefixer');

  require('scripts/hello-service.js');
  require('scripts/topology-service.js');

  // modules.get('a4c-plugin-sample').controller('HelloController', ['$scope', 'helloService',
  //   function($scope, helloService) {
  //     $scope.hello = helloService.get({name: 'world'});
  //   }
  // ]);

  modules.get('a4c-plugin-sample',['a4c-applications','a4c-topology-editor','ui.bootstrap', 'ui.select']).controller('HelloController', ['$scope', '$location', 'topologyServices','topologyEditor','soeService',
    function($scope, $location, topologyServices, topologyEditor, soeService) {

      topologyEditor($scope);
      $scope.clTopologyStatus="NOT_STARTED";
      $scope.clPrintingStatus="NOT STARTED";
      $scope.autodeploy = false;
      $scope.record = false;
      $scope.repeat = 0;
      // if(!$scope.sdeEndpoint){
      //
      //   $scope.sdeEndpoint=$location.host().split(":")[0] + ":8080";
      // }
      console.log($scope.topologyId);
      var appDetails = $scope.topologyId.split(":");




      $scope.oldNodes=[];
      $scope.newNodes=[];
      $scope.replacements=[];
      $scope.finishedChanges=false;
      $scope.performance = ["Low", "Medium", "Premium"];
      $scope.selectedPerformance = {item: $scope.performance[0]};

      soeService.optimizationStatus.receive({appName: appDetails[0], appVersion: appDetails[1]},
          function (optimization) {

            if(optimization.status == "SUCCESS"){

              $scope.clTopologyStatus="OPTIMISED";
              $scope.clPrintingStatus="OPTIMISED";
              for(var i in optimization.requestList){
                $scope.oldNodes.push(optimization.requestList[i].name);
              }
              for(var i in optimization.responseList){
                $scope.newNodes.push(optimization.responseList[i].type.split(":")[0]);
              }
              $scope.finishedChanges=true;

            }else if(optimization.status == "PROCESSING"){
              $scope.clTopologyStatus="IN_PROGRESS";
              $scope.clPrintingStatus="IN PROGRESS";

            }else if(optimization.status == "FAIL"){
              $scope.clTopologyStatus="ERROR";
              $scope.clPrintingStatus=optimization.status;
            }

          });



      $scope.cloudlightningTopo=function() {
        $scope.oldNodes=[];
        $scope.newNodes=[];
        $scope.replacements=[];
        $scope.clTopologyStatus = "IN_PROGRESS";
        // console.log("topo status=" + $scope.clTopologyStatus);
        $scope.clPrintingStatus = "IN PROGRESS";

        topologyServices.dao.get({
          topologyId: $scope.topologyId
        }, function (successResult) {

          // console.log("Topology:" + JSON.stringify(successResult));

          var clRequest = prepareClRequest(successResult.data, $scope.selectedPerformance.item);
          console.log("Cl Request with impl new:"+JSON.stringify(clRequest));

          var nodeTemplatesList = getNodeTemplatesList(successResult.data);

          // console.log("NodeTemplatesList:"+JSON.stringify(nodeTemplatesList));

          soeService.cloudlightningResponseDocument.receive(
              clRequest, function (clResponse) {
                $scope.topologyJson = clResponse;

                // console.log("ClResponse:");
                // console.log(clResponse);



                $scope.clTopo.editClTopo(clRequest["services"], clResponse["responseList"], nodeTemplatesList, 0, 0,null,
                    function (prevOpId){
                      $scope.clTopo.applyAdditionalRelationships(clResponse["additionalOperations"], 0, prevOpId, $scope.autodeploy, clResponse["applicationName"] + ":" + clResponse["applicationVersion"], clResponse["environmentId"]);
                    });


              });
        });
      }


      $scope.cloudlightningTopoRedirect=function () {

        var fullPath=$location.path();
        var entities=fullPath.split("/");
        $location.path("/applications/details/"+entities[entities.length-2]+"/topology/editor");

      }

      $scope.cloudlightningTopoRelease=function () {
        $scope.oldNodes=[];
        $scope.newNodes=[];
        $scope.replacements=[];
        soeService.optimizationStatus.delete({appName: appDetails[0], appVersion: appDetails[1]},
            function (clResponse) {
              if(clResponse["response"]){
                $scope.topologyJson = clResponse;

                // console.log("ClResponse:");
                // console.log(clResponse);

                $scope.clTopo.editClTopo(clResponse["services"], clResponse["response"], null, 0, 0,null,
                    function (prevOpId) {
                      $scope.clTopo.deleteNodes(clResponse["toDelete"], 0, prevOpId, $scope.autodeploy);
                    });
              }else{
                //recovering from failed resource discovery -> deleting previous request.
                $scope.clTopologyStatus="NOT_STARTED";
                $scope.clPrintingStatus="NOT STARTED";
              }
            });


      }

      $scope.setSdeEndpoint=function (endpoint) {

        $scope.sdeEndpoint = endpoint;

      }

      $scope.setSelectedPerformance=function (perf) {

        $scope.selectedPerformance = perf;

      }





    }
  ]);

  var templateUrl = prefixer.prefix('views/hello.html');
  // register plugin state
  // states.state('a4cpluginsample', {
  //   url: '/a4c-plugin-sample',
  //   templateUrl: templateUrl,
  //   controller: 'HelloController',
  //   menu: {
  //     id: 'menu.a4c-plugin-sample',
  //     state: 'a4cpluginsample',
  //     key: 'Hello plugin',
  //     icon: 'fa fa-coffee',
  //     priority: 11000,
  //     roles: ['ADMIN']
  //   }
  // });


  var prefix = 'editor_app_env.editor'
  states.state(prefix + '.optimise', {
    url: '/soe',
    templateUrl: templateUrl,
    controller: 'HelloController',
    menu: {
      id: 'am.' + prefix + '.optimise',
      state: prefix + '.optimise',
      key: 'Find Optimal',
      icon: 'fa fa-bolt',
      show: true,
      priority: 45
    }
  });
});
