/*
 * Copyright 1999-2015 dangdang.com.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package com.dangdang.ddframe.rdb.sharding.jdbc.core.statement;

import com.dangdang.ddframe.rdb.sharding.executor.type.statement.StatementExecutor;
import com.dangdang.ddframe.rdb.sharding.executor.type.statement.StatementUnit;
import com.dangdang.ddframe.rdb.sharding.jdbc.adapter.AbstractStatementAdapter;
import com.dangdang.ddframe.rdb.sharding.jdbc.core.connection.ShardingConnection;
import com.dangdang.ddframe.rdb.sharding.jdbc.core.resultset.GeneratedKeysResultSet;
import com.dangdang.ddframe.rdb.sharding.jdbc.core.resultset.ShardingResultSet;
import com.dangdang.ddframe.rdb.sharding.merger.core.MergeResultSet;
import com.dangdang.ddframe.rdb.sharding.merger.core.MergeResultSetFactory;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.context.GeneratedKey;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.statement.insert.InsertStatement;
import com.dangdang.ddframe.rdb.sharding.routing.SQLExecutionUnit;
import com.dangdang.ddframe.rdb.sharding.routing.SQLRouteResult;
import com.dangdang.ddframe.rdb.sharding.routing.StatementRoutingEngine;
import com.google.common.base.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * 支持分片的静态语句对象.
 * 
 * @author gaohongtao
 * @author caohao
 * @author zhangliang
 */
public class ShardingStatement extends AbstractStatementAdapter {
    
    @Getter(AccessLevel.PROTECTED)
    private final ShardingConnection shardingConnection;
    
    @Getter(AccessLevel.PROTECTED)
    private boolean returnGeneratedKeys;
    
    @Getter
    private final int resultSetType;
    
    @Getter
    private final int resultSetConcurrency;
    
    @Getter
    private final int resultSetHoldability;
    
    @Getter
    private final Collection<Statement> routedStatements = new LinkedList<>();
    
    @Getter(AccessLevel.PROTECTED)
    @Setter(AccessLevel.PROTECTED)
    private SQLRouteResult routeResult;
    
    @Setter(AccessLevel.PROTECTED)
    private ResultSet currentResultSet;
    
    public ShardingStatement(final ShardingConnection shardingConnection) {
        this(shardingConnection, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.HOLD_CURSORS_OVER_COMMIT);
    }
    
    public ShardingStatement(final ShardingConnection shardingConnection, final int resultSetType, final int resultSetConcurrency) {
        this(shardingConnection, resultSetType, resultSetConcurrency, ResultSet.HOLD_CURSORS_OVER_COMMIT);
    }
    
    public ShardingStatement(final ShardingConnection shardingConnection, final int resultSetType, final int resultSetConcurrency, final int resultSetHoldability) {
        super(Statement.class);
        this.shardingConnection = shardingConnection;
        this.resultSetType = resultSetType;
        this.resultSetConcurrency = resultSetConcurrency;
        this.resultSetHoldability = resultSetHoldability;
    }
    
    @Override
    public Connection getConnection() throws SQLException {
        return shardingConnection;
    }
    
    @Override
    public ResultSet executeQuery(final String sql) throws SQLException {
        ResultSet result;
        try {
            //result = ResultSetFactory.getResultSet(generateExecutor(sql).executeQuery(), routeResult.getSqlStatement());
    
    
            List<ResultSet> resultSets = generateExecutor(sql).executeQuery();
            Optional<MergeResultSet> mergeResultSet = MergeResultSetFactory.getResultSet(resultSets, getRouteResult().getSqlStatement());
            if (mergeResultSet.isPresent()) {
                result = new ShardingResultSet(resultSets, getRouteResult().getSqlStatement(), mergeResultSet.get());
            } else {
                result = resultSets.get(0);
            }
        } finally {
            setCurrentResultSet(null);
        }
        setCurrentResultSet(result);
        return result;
    }
    
    @Override
    public int executeUpdate(final String sql) throws SQLException {
        try {
            return generateExecutor(sql).executeUpdate();
        } finally {
            setCurrentResultSet(null);
        }
    }
    
    @Override
    public int executeUpdate(final String sql, final int autoGeneratedKeys) throws SQLException {
        if (RETURN_GENERATED_KEYS == autoGeneratedKeys) {
            markReturnGeneratedKeys();
        }
        try {
            return generateExecutor(sql).executeUpdate(autoGeneratedKeys);
        } finally {
            setCurrentResultSet(null);
        }
    }
    
    @Override
    public int executeUpdate(final String sql, final int[] columnIndexes) throws SQLException {
        markReturnGeneratedKeys();
        try {
            return generateExecutor(sql).executeUpdate(columnIndexes);
        } finally {
            setCurrentResultSet(null);
        }
    }
    
    @Override
    public int executeUpdate(final String sql, final String[] columnNames) throws SQLException {
        markReturnGeneratedKeys();
        try {
            return generateExecutor(sql).executeUpdate(columnNames);
        } finally {
            setCurrentResultSet(null);
        }
    }
    
    @Override
    public boolean execute(final String sql) throws SQLException {
        try {
            return generateExecutor(sql).execute();
        } finally {
            setCurrentResultSet(null);
        }
    }
    
    @Override
    public boolean execute(final String sql, final int autoGeneratedKeys) throws SQLException {
        if (RETURN_GENERATED_KEYS == autoGeneratedKeys) {
            markReturnGeneratedKeys();
        }
        try {
            return generateExecutor(sql).execute(autoGeneratedKeys);
        } finally {
            setCurrentResultSet(null);
        }
    }
    
    @Override
    public boolean execute(final String sql, final int[] columnIndexes) throws SQLException {
        markReturnGeneratedKeys();
        try {
            return generateExecutor(sql).execute(columnIndexes);
        } finally {
            setCurrentResultSet(null);
        }
    }
    
    @Override
    public boolean execute(final String sql, final String[] columnNames) throws SQLException {
        markReturnGeneratedKeys();
        try {
            return generateExecutor(sql).execute(columnNames);
        } finally {
            setCurrentResultSet(null);
        }
    }
    
    protected final void markReturnGeneratedKeys() {
        returnGeneratedKeys = true;
    }
    
    private StatementExecutor generateExecutor(final String sql) throws SQLException {
        clearPrevious();
        routeResult = new StatementRoutingEngine(shardingConnection.getShardingContext()).route(sql);
        Collection<StatementUnit> statementUnits = new LinkedList<>();
        for (SQLExecutionUnit each : routeResult.getExecutionUnits()) {
            Statement statement = shardingConnection.getConnection(
                    each.getDataSource(), routeResult.getSqlStatement().getType()).createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
            replayMethodsInvocation(statement);
            statementUnits.add(new StatementUnit(each, statement));
            routedStatements.add(statement);
        }
        return new StatementExecutor(shardingConnection.getShardingContext().getExecutorEngine(), routeResult.getSqlStatement().getType(), statementUnits);
    }
    
    private void clearPrevious() throws SQLException {
        for (Statement each : routedStatements) {
            each.close();
        }
        routedStatements.clear();
    }
    
    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        Optional<GeneratedKey> generatedKey = getGeneratedKey();
        if (generatedKey.isPresent() && returnGeneratedKeys) {
            return new GeneratedKeysResultSet(routeResult.getGeneratedKeys().iterator(), generatedKey.get().getColumn(), this);
        }
        return new GeneratedKeysResultSet();
    }
    
    protected final Optional<GeneratedKey> getGeneratedKey() {
        if (null != routeResult && routeResult.getSqlStatement() instanceof InsertStatement) {
            return Optional.fromNullable(((InsertStatement) routeResult.getSqlStatement()).getGeneratedKey());
        }
        return Optional.absent();
    }
    
    @Override
    public ResultSet getResultSet() throws SQLException {
        if (null != currentResultSet) {
            return currentResultSet;
        }
        if (1 == routedStatements.size()) {
            currentResultSet = routedStatements.iterator().next().getResultSet();
            return currentResultSet;
        }
        List<ResultSet> resultSets = new ArrayList<>(routedStatements.size());
        for (Statement each : routedStatements) {
            resultSets.add(each.getResultSet());
        }
        //currentResultSet = ResultSetFactory.getResultSet(resultSets, routeResult.getSqlStatement());
    
        Optional<MergeResultSet> mergeResultSet = MergeResultSetFactory.getResultSet(resultSets, getRouteResult().getSqlStatement());
        if (mergeResultSet.isPresent()) {
            currentResultSet = new ShardingResultSet(resultSets, getRouteResult().getSqlStatement(), mergeResultSet.get());
        } else {
            currentResultSet = resultSets.get(0);
        }
        return currentResultSet;
    }
}
