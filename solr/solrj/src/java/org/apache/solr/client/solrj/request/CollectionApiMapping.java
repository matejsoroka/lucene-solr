/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.solr.client.solrj.request;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.common.params.CollectionParams.CollectionAction;
import org.apache.solr.common.params.ConfigSetParams.ConfigSetAction;
import org.apache.solr.common.util.CommandOperation;
import org.apache.solr.common.util.Pair;
import org.apache.solr.common.util.Utils;

import static org.apache.solr.client.solrj.SolrRequest.METHOD.DELETE;
import static org.apache.solr.client.solrj.SolrRequest.METHOD.GET;
import static org.apache.solr.client.solrj.SolrRequest.METHOD.POST;
import static org.apache.solr.client.solrj.request.CollectionApiMapping.ConfigSetEndPoint.CONFIG_COMMANDS;
import static org.apache.solr.client.solrj.request.CollectionApiMapping.ConfigSetEndPoint.CONFIG_DEL;
import static org.apache.solr.client.solrj.request.CollectionApiMapping.ConfigSetEndPoint.LIST_CONFIG;
import static org.apache.solr.client.solrj.request.CollectionApiMapping.EndPoint.CLUSTER;
import static org.apache.solr.client.solrj.request.CollectionApiMapping.EndPoint.CLUSTER_ALIASES;
import static org.apache.solr.client.solrj.request.CollectionApiMapping.EndPoint.CLUSTER_CMD;
import static org.apache.solr.client.solrj.request.CollectionApiMapping.EndPoint.CLUSTER_CMD_STATUS;
import static org.apache.solr.client.solrj.request.CollectionApiMapping.EndPoint.CLUSTER_CMD_STATUS_DELETE;
import static org.apache.solr.client.solrj.request.CollectionApiMapping.EndPoint.CLUSTER_NODES;
import static org.apache.solr.client.solrj.request.CollectionApiMapping.EndPoint.COLLECTIONS;
import static org.apache.solr.client.solrj.request.CollectionApiMapping.EndPoint.COLLECTIONS_COMMANDS;
import static org.apache.solr.client.solrj.request.CollectionApiMapping.EndPoint.COLLECTION_STATE;
import static org.apache.solr.client.solrj.request.CollectionApiMapping.EndPoint.PER_COLLECTION;
import static org.apache.solr.client.solrj.request.CollectionApiMapping.EndPoint.PER_COLLECTION_PER_SHARD_COMMANDS;
import static org.apache.solr.client.solrj.request.CollectionApiMapping.EndPoint.PER_COLLECTION_PER_SHARD_DELETE;
import static org.apache.solr.client.solrj.request.CollectionApiMapping.EndPoint.PER_COLLECTION_PER_SHARD_PER_REPLICA_DELETE;
import static org.apache.solr.client.solrj.request.CollectionApiMapping.EndPoint.PER_COLLECTION_SHARDS_COMMANDS;
import static org.apache.solr.common.params.CollectionParams.CollectionAction.*;
import static org.apache.solr.common.params.CommonParams.NAME;

/**
 * Stores the mapping of v1 API parameters to v2 API parameters
 * for the collection API and the configset API.
 */
public class CollectionApiMapping {

  public enum Meta implements CommandMeta {
    GET_COLLECTIONS(COLLECTIONS, GET, LIST),
    GET_CLUSTER(CLUSTER, GET, LIST, "/cluster", null),
    GET_CLUSTER_OVERSEER(CLUSTER, GET, OVERSEERSTATUS, "/cluster/overseer", null),
    GET_CLUSTER_STATUS_CMD(CLUSTER_CMD_STATUS, GET, REQUESTSTATUS),
    DELETE_CLUSTER_STATUS(CLUSTER_CMD_STATUS_DELETE, DELETE, DELETESTATUS),
    GET_A_COLLECTION(COLLECTION_STATE, GET, CLUSTERSTATUS),
    LIST_ALIASES(CLUSTER_ALIASES, GET, LISTALIASES),
    CREATE_COLLECTION(COLLECTIONS_COMMANDS,
        POST,
        CREATE,
        CREATE.toLower(),
        Utils.makeMap(
            "collection.configName", "config",
            "createNodeSet.shuffle", "shuffleNodes",
            "createNodeSet", "nodeSet"
        ),
        Utils.makeMap("property.", "properties.")),

    DELETE_COLL(EndPoint.PER_COLLECTION_DELETE,
        DELETE,
        CollectionAction.DELETE,
        CollectionAction.DELETE.toLower(),
        Utils.makeMap(NAME, "collection")),

    RELOAD_COLL(PER_COLLECTION,
        POST,
        RELOAD,
        RELOAD.toLower(),
        Utils.makeMap(NAME, "collection")),
    MODIFY_COLLECTION(PER_COLLECTION,
        POST,
        MODIFYCOLLECTION,
        "modify",null),
    MIGRATE_DOCS(PER_COLLECTION,
        POST,
        MIGRATE,
        "migrate-docs",
        Utils.makeMap("split.key", "splitKey",
            "target.collection", "target",
            "forward.timeout", "forwardTimeout"
        )),
    MOVE_REPLICA(PER_COLLECTION,
        POST, MOVEREPLICA, "move-replica", null),
    REBALANCE_LEADERS(PER_COLLECTION,
        POST,
        REBALANCELEADERS,
        "rebalance-leaders", null),
    CREATE_ALIAS(COLLECTIONS_COMMANDS,
        POST,
        CREATEALIAS,
        "create-alias",
        null),
    CREATE_ROUTED_ALIAS(COLLECTIONS_COMMANDS,
        POST,
        CREATEROUTEDALIAS,
        "create-routed-alias",
        // same as the CREATE_COLLECTION but with "create-collection" prefix
        CREATE_COLLECTION.paramsToAttrs.entrySet().stream().collect(Collectors.toMap(
            entry -> "create-collection." + entry.getKey(),
            entry -> "create-collection." + entry.getValue()
        )),
        CREATE_COLLECTION.prefixParamsToAttrs.entrySet().stream().collect(Collectors.toMap(
            entry -> "create-collection." + entry.getKey(),
            entry -> "create-collection." + entry.getValue()
        ))),
    DELETE_ALIAS(COLLECTIONS_COMMANDS,
        POST,
        DELETEALIAS,
        "delete-alias",
        null),
    MODIFY_ALIAS(COLLECTIONS_COMMANDS,
        POST,
        MODIFYALIAS,
        "modify-alias",
        null),
    CREATE_SHARD(PER_COLLECTION_SHARDS_COMMANDS,
        POST,
        CREATESHARD,
        "create",
        Utils.makeMap("createNodeSet", "nodeSet"),
        Utils.makeMap("property.", "coreProperties.")) {
      @Override
      public String getParamSubstitute(String param) {
        return super.getParamSubstitute(param);
      }
    },

    SPLIT_SHARD(PER_COLLECTION_SHARDS_COMMANDS,
        POST,
        SPLITSHARD,
        "split",
        Utils.makeMap(
            "split.key", "splitKey"),
        Utils.makeMap("property.", "coreProperties.")),
    DELETE_SHARD(PER_COLLECTION_PER_SHARD_DELETE,
        DELETE, DELETESHARD),

    CREATE_REPLICA(PER_COLLECTION_SHARDS_COMMANDS,
        POST,
        ADDREPLICA,
        "add-replica",
        null,
        Utils.makeMap("property.", "coreProperties.")),

    DELETE_REPLICA(PER_COLLECTION_PER_SHARD_PER_REPLICA_DELETE,
        DELETE, DELETEREPLICA),

    SYNC_SHARD(PER_COLLECTION_PER_SHARD_COMMANDS,
        POST,
        CollectionAction.SYNCSHARD,
        "synch-shard",
        null),
    ADD_REPLICA_PROPERTY(PER_COLLECTION,
        POST,
        CollectionAction.ADDREPLICAPROP,
        "add-replica-property",
        Utils.makeMap("property", "name", "property.value", "value")),
    DELETE_REPLICA_PROPERTY(PER_COLLECTION,
        POST,
        DELETEREPLICAPROP,
        "delete-replica-property",
        null),
    ADD_ROLE(CLUSTER_CMD,
        POST,
        ADDROLE,
        "add-role",null),
    REMOVE_ROLE(CLUSTER_CMD,
        POST,
        REMOVEROLE,
        "remove-role",null),

    SET_CLUSTER_PROPERTY(CLUSTER_CMD,
        POST,
        CLUSTERPROP,
        "set-property",null),
    UTILIZE_NODE(CLUSTER_CMD,
        POST,
        UTILIZENODE,
        "utilize-node",null),

    BACKUP_COLLECTION(COLLECTIONS_COMMANDS,
        POST,
        BACKUP,
        "backup-collection", null
    ),
    RESTORE_COLLECTION(COLLECTIONS_COMMANDS,
        POST,
        RESTORE,
        "restore-collection",
        null
    ),
    GET_NODES(CLUSTER_NODES, GET, null),
    FORCE_LEADER(PER_COLLECTION_PER_SHARD_COMMANDS, POST, CollectionAction.FORCELEADER, "force-leader", null),
    BALANCE_SHARD_UNIQUE(PER_COLLECTION, POST, BALANCESHARDUNIQUE,"balance-shard-unique" , null)
    ;

    public final String commandName;
    public final EndPoint endPoint;
    public final SolrRequest.METHOD method;
    public final CollectionAction action;

    //bi-directional mapping of v1 http param name to v2 json attribute
    public final Map<String, String> paramsToAttrs; // v1 -> v2
    public final Map<String, String> attrsToParams; // v2 -> v1
    //mapping of old prefix to new for instance properties.a=val can be substituted with property:{a:val}
    public final Map<String, String> prefixParamsToAttrs; // v1 -> v2

    public SolrRequest.METHOD getMethod() {
      return method;
    }


    Meta(EndPoint endPoint, SolrRequest.METHOD method, CollectionAction action) {
      this(endPoint, method, action, null, null);
    }

    Meta(EndPoint endPoint, SolrRequest.METHOD method, CollectionAction action,
         String commandName, Map paramsToAttrs) {
      this(endPoint, method, action, commandName, paramsToAttrs, Collections.emptyMap());
    }

    // lame... the Maps aren't typed simply because callers want to use Utils.makeMap which yields object vals
    @SuppressWarnings("unchecked")
    Meta(EndPoint endPoint, SolrRequest.METHOD method, CollectionAction action,
         String commandName, Map paramsToAttrs, Map prefixParamsToAttrs) {
      this.action = action;
      this.commandName = commandName;
      this.endPoint = endPoint;
      this.method = method;

      this.paramsToAttrs = paramsToAttrs == null ? Collections.emptyMap() : Collections.unmodifiableMap(paramsToAttrs);
      this.attrsToParams = Collections.unmodifiableMap(reverseMap(this.paramsToAttrs));
      this.prefixParamsToAttrs = prefixParamsToAttrs == null ? Collections.emptyMap() : Collections.unmodifiableMap(prefixParamsToAttrs);
    }

    private static Map<String, String> reverseMap(Map<String, String> input) { // swap keys and values
      Map<String, String> attrToParams = new HashMap<>(input.size());
      for (Map.Entry<String, String> entry :input.entrySet()) {
        final String existing = attrToParams.put(entry.getValue(), entry.getKey());
        if (existing != null) {
          throw new IllegalArgumentException("keys and values must collectively be unique");
        }
      }
      return attrToParams;
    }

    @Override
    public String getName() {
      return commandName;
    }

    @Override
    public SolrRequest.METHOD getHttpMethod() {
      return method;
    }

    @Override
    public V2EndPoint getEndPoint() {
      return endPoint;
    }

    // Returns iterator of v1 "params".
    @Override
    public Iterator<String> getParamNamesIterator(CommandOperation op) {
      Collection<String> paramNames = getParamNames_(op, this);
      Stream<String> pStream = paramNames.stream();
      if (!attrsToParams.isEmpty()) {
        pStream = pStream.map(paramName -> attrsToParams.getOrDefault(paramName, paramName));
      }
      if (!prefixParamsToAttrs.isEmpty()) {
        pStream = pStream.map(paramName -> {
          for (Map.Entry<String, String> e : prefixParamsToAttrs.entrySet()) {
            final String prefixV1 = e.getKey();
            final String prefixV2 = e.getValue();
            if (paramName.startsWith(prefixV2)) {
              return prefixV1 + paramName.substring(prefixV2.length()); // replace
            }
          }
          return paramName;
        });
      }
      return pStream.iterator();
    }

    // returns params (v1) from an underlying v2, with param (v1) input
    @Override
    public String getParamSubstitute(String param) {//input is v1
      for (Map.Entry<String, String> e : prefixParamsToAttrs.entrySet()) {
        final String prefixV1 = e.getKey();
        final String prefixV2 = e.getValue();
        if (param.startsWith(prefixV1)) {
          return prefixV2 + param.substring(prefixV1.length()); // replace
        }
      }
      return paramsToAttrs.getOrDefault(param, param);
    }

    // TODO document!
    public Object getReverseParamSubstitute(String param) {//input is v1
      for (Map.Entry<String, String> e : prefixParamsToAttrs.entrySet()) {
        final String prefixV1 = e.getKey();
        final String prefixV2 = e.getValue();
        if (param.startsWith(prefixV1)) {
          return new Pair<>(prefixV2.substring(0, prefixV2.length() - 1), param.substring(prefixV1.length()));
        }
      }
      return paramsToAttrs.getOrDefault(param, param);
    }

  }

  public enum EndPoint implements V2EndPoint {
    CLUSTER("cluster"),
    CLUSTER_ALIASES("cluster.aliases"),
    CLUSTER_CMD("cluster.Commands"),
    CLUSTER_NODES("cluster.nodes"),
    CLUSTER_CMD_STATUS("cluster.commandstatus"),
    CLUSTER_CMD_STATUS_DELETE("cluster.commandstatus.delete"),
    COLLECTIONS_COMMANDS("collections.Commands"),
    COLLECTIONS("collections"),
    COLLECTION_STATE("collections.collection"),
    PER_COLLECTION("collections.collection.Commands"),
    PER_COLLECTION_DELETE("collections.collection.delete"),
    PER_COLLECTION_SHARDS_COMMANDS("collections.collection.shards.Commands"),
    PER_COLLECTION_PER_SHARD_COMMANDS("collections.collection.shards.shard.Commands"),
    PER_COLLECTION_PER_SHARD_DELETE("collections.collection.shards.shard.delete"),
    PER_COLLECTION_PER_SHARD_PER_REPLICA_DELETE("collections.collection.shards.shard.replica.delete");
    final String specName;


    EndPoint(String specName) {
      this.specName = specName;
    }

    @Override
    public String getSpecName() {
      return specName;
    }
  }

  public interface V2EndPoint {

    String getSpecName();
  }

  public enum ConfigSetMeta implements CommandMeta {
    LIST(LIST_CONFIG, GET,null, ConfigSetAction.LIST),
    CREATE(CONFIG_COMMANDS, POST, "create", ConfigSetAction.CREATE),
    DEL(CONFIG_DEL,  DELETE, null, ConfigSetAction.DELETE)
    ;
    public final ConfigSetEndPoint endPoint;
    public final SolrRequest.METHOD method;
    public final String cmdName;
    public final ConfigSetAction action;


    ConfigSetMeta(ConfigSetEndPoint endPoint, SolrRequest.METHOD method, String cmdName, ConfigSetAction action) {
      this.cmdName = cmdName;
      this.endPoint = endPoint;
      this.method = method;
      this.action = action;
    }

    @Override
    public String getName() {
      return cmdName;
    }

    @Override
    public SolrRequest.METHOD getHttpMethod() {
      return method;
    }

    @Override
    public V2EndPoint getEndPoint() {
      return endPoint;
    }


  }
  public enum ConfigSetEndPoint implements V2EndPoint {
    LIST_CONFIG("cluster.configs"),
    CONFIG_COMMANDS("cluster.configs.Commands"),
    CONFIG_DEL("cluster.configs.delete");

    public final String spec;

    ConfigSetEndPoint(String spec) {
      this.spec = spec;
    }

    @Override
    public String getSpecName() {
      return spec;
    }
  }



  private static Collection<String> getParamNames_(CommandOperation op, CommandMeta command) {
    Object o = op.getCommandData();
    if (o instanceof Map) {
      Map map = (Map) o;
      List<String> result = new ArrayList<>();
      collectKeyNames(map, result, "");
      return result;
    } else {
      return Collections.emptySet();
    }
  }

  public static void collectKeyNames(Map<String, Object> map, List<String> result, String prefix) {
    for (Map.Entry<String, Object> e : map.entrySet()) {
      if (e.getValue() instanceof Map) {
        collectKeyNames((Map) e.getValue(), result, prefix + e.getKey() + ".");
      } else {
        result.add(prefix + e.getKey());
      }
    }
  }
  public interface CommandMeta {
    String getName();

    /**
     * the http method supported by this command
     */
    SolrRequest.METHOD getHttpMethod();

    V2EndPoint getEndPoint();

    default Iterator<String> getParamNamesIterator(CommandOperation op) {
      return getParamNames_(op, CommandMeta.this).iterator();
    }

    /** Given a v1 param, return the v2 attribute (possibly a dotted path). */
    default String getParamSubstitute(String name) {
      return name;
    }
  }
}