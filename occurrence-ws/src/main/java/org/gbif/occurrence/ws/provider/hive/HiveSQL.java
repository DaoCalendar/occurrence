package org.gbif.occurrence.ws.provider.hive;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.apache.commons.compress.utils.Lists;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.codehaus.jackson.node.TextNode;
import org.gbif.occurrence.ws.provider.hive.Result.Read;
import org.gbif.occurrence.ws.provider.hive.Result.ReadDescribe;
import org.gbif.occurrence.ws.provider.hive.Result.ReadExplain;
import org.gbif.occurrence.ws.provider.hive.query.validator.DatasetKeyAndLicenseRequiredRule;
import org.gbif.occurrence.ws.provider.hive.query.validator.OnlyPureSelectQueriesAllowedRule;
import org.gbif.occurrence.ws.provider.hive.query.validator.OnlyOneSelectAllowedRule;
import org.gbif.occurrence.ws.provider.hive.query.validator.Query.Issue;
import org.gbif.occurrence.ws.provider.hive.query.validator.QueryContext;
import org.gbif.occurrence.ws.provider.hive.query.validator.Rule;
import org.gbif.occurrence.ws.provider.hive.query.validator.SQLShouldBeExecutableRule;
import org.gbif.occurrence.ws.provider.hive.query.validator.TableNameShouldBeOccurrenceRule;
import com.google.common.base.Throwables;

/**
 * 
 * SQL class to validate and explain the query.
 *
 */
public class HiveSQL {

  /**
   * Explains the query, in case it is not compilable throws RuntimeException.
   */
  public static class Execute implements BiFunction<String, Read, String> {

    private static final String DESCRIBE = "DESCRIBE ";
    private static final String EXPLAIN = "EXPLAIN ";

    public String explain(String query) {
      return apply(EXPLAIN.concat(query), new ReadExplain());
    }

    public String describe(String tableName) {
      return apply(DESCRIBE.concat(tableName), new ReadDescribe());
    }

    @Override
    public String apply(String query, Read read) {
      try (Connection conn = ConnectionPool.nifiPoolFromDefaultProperties().getConnection();
          Statement stmt = conn.createStatement();
          ResultSet result = stmt.executeQuery(query);) {
        return read.apply(result);
      } catch (Exception ex) {
        throw Throwables.propagate(ex);
      }
    }

  }


  /**
   * 
   * Validate SQL download query for list of checks and return {@link Result}.
   *
   */
  public static class Validate implements Function<String, HiveSQL.Validate.Result> {

    private static final List<Rule> ruleBase = Arrays.asList(new OnlyPureSelectQueriesAllowedRule(), new OnlyOneSelectAllowedRule(), new DatasetKeyAndLicenseRequiredRule(), new TableNameShouldBeOccurrenceRule());
    
    public static class Result {
      private final String sql;
      private final List<Issue> issues;
      private final boolean ok;
      private final String explain;

      Result(String sql, List<Issue> issues, String queryExplanation, boolean ok) {
        this.sql = sql;
        this.issues = issues;
        this.ok = ok;
        this.explain = queryExplanation;
      }

      public String sql() {
        return sql;
      }

      public List<Issue> issues() {
        return issues;
      }

      public boolean isOk() {
        return ok;
      }

      public String explain() {
        return explain;
      }

      @Override
      public String toString() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("sql", sql);
        ArrayNode issuesNode = JsonNodeFactory.instance.arrayNode();
        issues.forEach(issue -> issuesNode.add(issue.description().concat(issue.comment())));
        node.put("issues", issuesNode);
        node.put("explain", new TextNode(explain));
        node.put("ok", isOk());
        return node.toString();
      }
    }

    @Override
    public HiveSQL.Validate.Result apply(String sql) {
      List<Issue> issues = Lists.newArrayList();

      QueryContext context = QueryContext.from(sql).onParseFail(issues::add);
      if (context.hasParseIssue())
        return new Result(context.sql(), issues, SQLShouldBeExecutableRule.COMPILATION_ERROR, issues.isEmpty());

      
      ruleBase.forEach(rule -> rule.apply(context).onViolation(issues::add));

      // SQL should be executable.
      SQLShouldBeExecutableRule executableRule = new SQLShouldBeExecutableRule();
      executableRule.apply(context).onViolation(issues::add);

      return new Result(context.sql(), issues, executableRule.explainValue(), issues.isEmpty());
    }

  }

}
