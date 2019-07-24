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

package org.apache.shardingsphere.core.rewrite.token.generator;

import com.google.common.base.Optional;
import org.apache.shardingsphere.core.optimize.api.statement.InsertOptimizedStatement;
import org.apache.shardingsphere.core.optimize.api.statement.OptimizedStatement;
import org.apache.shardingsphere.core.parse.sql.segment.dml.column.InsertColumnsSegment;
import org.apache.shardingsphere.core.rewrite.builder.ParameterBuilder;
import org.apache.shardingsphere.core.rewrite.token.pojo.InsertColumnsToken;
import org.apache.shardingsphere.core.rule.BaseRule;
import org.apache.shardingsphere.core.rule.EncryptRule;
import org.apache.shardingsphere.core.rule.MasterSlaveRule;
import org.apache.shardingsphere.core.rule.ShardingRule;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;

/**
 * Insert columns token generator.
 *
 * @author panjuan
 */
public final class InsertColumnsTokenGenerator implements OptionalSQLTokenGenerator<BaseRule> {
    
    private BaseRule baseRule;
    
    private InsertOptimizedStatement insertOptimizedStatement;
    
    private String tableName;
    
    @Override
    public Optional<InsertColumnsToken> generateSQLToken(final OptimizedStatement optimizedStatement, 
                                                         final ParameterBuilder parameterBuilder, final BaseRule rule, final boolean isQueryWithCipherColumn) {
        if (isNotNeedToGenerateSQLToken(rule, optimizedStatement)) {
            return Optional.absent();
        }
        initParameters((InsertOptimizedStatement) optimizedStatement, rule);
        return createInsertColumnsToken();
    }
    
    private boolean isNotNeedToGenerateSQLToken(final BaseRule rule, final OptimizedStatement optimizedStatement) {
        Optional<InsertColumnsSegment> insertColumnsSegment = optimizedStatement.getSQLStatement().findSQLSegment(InsertColumnsSegment.class);
        return rule instanceof MasterSlaveRule && !(optimizedStatement instanceof InsertOptimizedStatement && insertColumnsSegment.isPresent());
    }
    
    private void initParameters(final InsertOptimizedStatement optimizedStatement, final BaseRule rule) {
        baseRule = rule;
        this.insertOptimizedStatement = optimizedStatement;
        tableName = insertOptimizedStatement.getTables().getSingleTableName();
    }
    
    private Optional<InsertColumnsToken> createInsertColumnsToken() {
        InsertColumnsSegment segment = insertOptimizedStatement.getSQLStatement().findSQLSegment(InsertColumnsSegment.class).get();
        if (!segment.getColumns().isEmpty()) {
            return Optional.absent();
        }
        InsertColumnsToken result = new InsertColumnsToken(segment.getStopIndex(), getActualInsertColumns(), !isNeededToAppendColumns());
        return Optional.of(result);
    }
    
    private Collection<String> getActualInsertColumns() {
        Collection<String> result = new LinkedList<>();
        Map<String, String> logicAndCipherColumns = getEncryptRule().getEncryptEngine().getLogicAndCipherColumns(tableName);
        for (String each : insertOptimizedStatement.getInsertColumns().getRegularColumnNames()) {
            result.add(getCipherColumn(each, logicAndCipherColumns));
        }
        return result;
    }
    
    private EncryptRule getEncryptRule() {
        return baseRule instanceof ShardingRule ? ((ShardingRule) baseRule).getEncryptRule() : (EncryptRule) baseRule;
    }
    
    private String getCipherColumn(final String column, final Map<String, String> logicAndCipherColumns) {
        return logicAndCipherColumns.keySet().contains(column) ? logicAndCipherColumns.get(column) : column;
    }
    
    private boolean isNeededToAppendColumns() {
        return baseRule instanceof ShardingRule ? isNeededToAppendColumns((ShardingRule) baseRule) : isNeededToAppendEncryptColumns((EncryptRule) baseRule);
    }
    
    private boolean isNeededToAppendColumns(final ShardingRule shardingRule) {
        return isNeededToAppendGeneratedKey(shardingRule) || isNeededToAppendEncryptColumns(shardingRule.getEncryptRule());
    }
    
    private boolean isNeededToAppendGeneratedKey(final ShardingRule shardingRule) {
        Optional<String> generateKeyColumnName = shardingRule.findGenerateKeyColumnName(tableName);
        return generateKeyColumnName.isPresent() && !insertOptimizedStatement.getInsertColumns().getRegularColumnNames().contains(generateKeyColumnName.get());
    }
    
    private boolean isNeededToAppendEncryptColumns(final EncryptRule encryptRule) {
        return encryptRule.getEncryptEngine().getAssistedQueryAndPlainColumnCount(tableName) > 0;
    }
}
