package com.alibaba.cloud.ai.application.websearch.rag.preretrieval.query.expansion;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.util.PromptAssert;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * @author yuluo
 * @author <a href="mailto:yuluo08290126@gmail.com">yuluo</a>
 */

public class MultiQueryExpander implements QueryExpander {

	private static final Logger logger = LoggerFactory.getLogger(MultiQueryExpander.class);

	private static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = new PromptTemplate(
			"""
					你是一位信息检索与搜索优化的专家。
					请生成 {number} 个不同版本的给定查询。
								
					每个变体应涵盖主题的不同视角或方面，同时保持原始查询的核心意图。目标是扩展搜索范围，提高找到相关信息的机会。
								
					请勿解释选择或添加其他文本。
					提供查询变体，并以换行符分隔。
								
					原始查询：{query}
								
					查询变体：
					"""
	);

	private static final Boolean DEFAULT_INCLUDE_ORIGINAL = true;

	private static final Integer DEFAULT_NUMBER_OF_QUERIES = 5;

	private final ChatClient chatClient;

	private final PromptTemplate promptTemplate;

	private final boolean includeOriginal;

	private final int numberOfQueries;

	public MultiQueryExpander(
			ChatClient.Builder chatClientBuilder,
			@Nullable PromptTemplate promptTemplate,
			@Nullable Boolean includeOriginal,
			@Nullable Integer numberOfQueries
	) {
		Assert.notNull(chatClientBuilder, "ChatClient.Builder must not be null");

		this.chatClient = chatClientBuilder.build();
		this.promptTemplate = promptTemplate == null ? DEFAULT_PROMPT_TEMPLATE : promptTemplate;
		this.includeOriginal = includeOriginal == null ? DEFAULT_INCLUDE_ORIGINAL : includeOriginal;
		this.numberOfQueries = numberOfQueries == null ? DEFAULT_NUMBER_OF_QUERIES : numberOfQueries;

		PromptAssert.templateHasRequiredPlaceholders(this.promptTemplate, "number", "query");
	}

	@Override
	public List<Query> expand(Query query) {

		Assert.notNull(query, "Query must not be null");

		logger.debug("Generating {} queries for query: {}", this.numberOfQueries, query.text());

		String resp = this.chatClient.prompt()
				.user(user -> user.text(this.promptTemplate.getTemplate())
						.param("number", this.numberOfQueries)
						.param("query", query.text()))
				.call()
				.content();

		if (Objects.isNull(resp)) {

			logger.warn("No response from chat client for query: {}. is return.", query.text());
			return List.of(query);
		}

		List<String> queryVariants = Arrays.stream(resp.split("\n")).filter(StringUtils::hasText).toList();

		if (CollectionUtils.isEmpty(queryVariants) || this.numberOfQueries != queryVariants.size()) {

			logger.warn("Query expansion result dose not contain the requested {} variants for query: {}. is return.", this.numberOfQueries, query.text());
			return List.of(query);
		}

		List<Query> queries = queryVariants.stream()
				.filter(StringUtils::hasText)
				.map(queryText -> query.mutate().text(queryText).build())
				.collect(Collectors.toList());

		if (this.includeOriginal) {

			logger.debug("Including original query in the expanded queries for query: {}", query.text());
			queries.add(0, query);
		}

		return queries;
	}

	public static final class Builder {

		private ChatClient.Builder chatClientBuilder;

		private PromptTemplate promptTemplate;

		private Boolean includeOriginal;

		private Integer numberOfQueries;

		private Builder() {
		}

		public Builder chatClientBuilder(ChatClient.Builder chatClientBuilder) {
			this.chatClientBuilder = chatClientBuilder;
			return this;
		}

		public Builder promptTemplate(PromptTemplate promptTemplate) {
			this.promptTemplate = promptTemplate;
			return this;
		}

		public Builder includeOriginal(Boolean includeOriginal) {
			this.includeOriginal = includeOriginal;
			return this;
		}

		public Builder numberOfQueries(Integer numberOfQueries) {
			this.numberOfQueries = numberOfQueries;
			return this;
		}

		public MultiQueryExpander build() {
			return new MultiQueryExpander(this.chatClientBuilder, this.promptTemplate, this.includeOriginal, this.numberOfQueries);
		}

	}

}
