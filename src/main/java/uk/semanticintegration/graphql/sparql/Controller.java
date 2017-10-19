package uk.semanticintegration.graphql.sparql;

import static spark.Spark.get;
import static spark.Spark.port;
import static spark.Spark.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import spark.ModelAndView;
import spark.template.velocity.VelocityTemplateEngine;

/**
 * Created by szymon on 05/09/2017.
 */
public class Controller {


  public static void start(Config config, GraphQL graphQL) {

    port(config.graphql.port);

    System.out.println("GraphQL server started at: " + config.graphql.port);

    get(config.graphql.graphiql, (req, res) -> {
      Map<String, String> model = new HashMap<>();
      String portScript = config.graphql.port.toString();
      model.put("template", portScript);
      return new VelocityTemplateEngine().render(
          new ModelAndView(model, "graphiql.vtl")
      );
    });

    post(config.graphql.path, (req, res) -> {

      Converter converter = new Converter(config);

      ObjectMapper mapper = new ObjectMapper();
      JsonNode requestObject = mapper.readTree(req.body().toString());

      String query = requestObject.get("query").asText();

      Map<String, Object> result = new HashMap<>();
      Map<String, Object> data = new HashMap<>();
      List<GraphQLError> errors = new ArrayList<>();
      Map<Object, Object> extensions = new HashMap<>();
      result.put("data", data);
      result.put("errors", errors);
      result.put("extensions", extensions);

      ExecutionInput executionInput;
      ExecutionResult qlResult;

      List<String> sparqlQueries;

      if (!query.contains("IntrospectionQuery")) {

        sparqlQueries = converter.graphql2sparql(query);

        SparqlClient client = new SparqlClient(sparqlQueries, config.sparqlEndpointsContext);

        executionInput = ExecutionInput.newExecutionInput()
            .query(query)
            .context(client)
            .build();

        qlResult = graphQL.execute(executionInput);

        extensions.put("json-ld", converter.graphql2jsonld(qlResult.getData()));
        extensions.put("sparql-queries", sparqlQueries);

      } else qlResult = graphQL.execute(query);

      data.putAll(qlResult.getData());
      errors.addAll(qlResult.getErrors());

      JsonNode resultJson = mapper.readTree(new ObjectMapper().writeValueAsString(result));
      res.type("application/json");

      return resultJson;

    });
  }

}