package quorachallenge;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.TreeSet;

import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import quorachallenge.DataEntry;
import quorachallenge.DataEntryType;
import quorachallenge.ResultDataEntry;
import quorachallenge.SizeComparableSet;
import quorachallenge.TokenDictionary;

public class TypeaheadSearchTest {

	private static ArrayList<String> corpus;
	private static final int TOKEN_SIZE = 100;
	private static final int DENSITY_FACTOR = 5;
	private static final int CORPUS_SIZE = TOKEN_SIZE * DENSITY_FACTOR;
	private static final int PREFIX_LEN = 5;
	private static final Random rand = new Random();

	@BeforeClass
	public static void setUpBeforeClass() {
		corpus = generateCorpus();

	}

	@After
	public void tearDown() {
		TokenDictionary.reset();
	}

	private static ArrayList<String> generateCorpus() {
		ArrayList<String> list = new ArrayList<>();

		for (int i = 0; i < CORPUS_SIZE; ++i) {
			StringBuilder sb = new StringBuilder();
			for (int j = 0; j < PREFIX_LEN; ++j) {
				sb.append((char) ('A' + 0x20 * rand.nextInt(2)));
			}
			list.add(sb.toString() + (i / DENSITY_FACTOR));
		}
		return list;
	}

	@Test
	public void testTokenDictionaryLookupAndAdd() {
		for (int i = 0; i < corpus.size(); ++i) {
			TokenDictionary.lookupOrAdd(corpus.get(i));
		}
		Assert.assertEquals(TOKEN_SIZE, TokenDictionary.getDictSize());
	}

	@Test
	public void testResultDataEntryCompareTo() throws InterruptedException {
		ResultDataEntry small = new ResultDataEntry(null, 1);
		ResultDataEntry big = new ResultDataEntry(null, 1.1f);
		ResultDataEntry rde1 = small, rde2 = big;
		Assert.assertTrue(rde1.compareTo(rde2) < 0);

		rde1 = big;
		rde2 = small;
		Assert.assertTrue(rde1.compareTo(rde2) > 0);

		DataEntry ode = new DataEntry(DataEntryType.user, "x1", 1.0f, "");
		DataEntry nde = new DataEntry(DataEntryType.user, "x1", 1.0f, "");
		ResultDataEntry newer = new ResultDataEntry(nde, 1.0f);
		ResultDataEntry older = new ResultDataEntry(ode, 1.0f);

		rde1 = older;
		rde2 = newer;

		Assert.assertTrue(rde1.compareTo(rde2) < 0);

		rde1 = newer;
		rde2 = older;
		Assert.assertTrue(rde1.compareTo(rde2) > 0);
	}

	@Test
	public void testDataEntryTokenize() {
		int num_words = 100;
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < num_words; ++i) {
			sb.append(corpus.get(i));
			sb.append(rand.nextBoolean() ? " " : "\t");
		}
		DataEntry.tokenize(sb.toString());
		Assert.assertEquals(num_words / DENSITY_FACTOR + PREFIX_LEN,
				TokenDictionary.getDictSize());
	}

	@Test
	public void testDataEntryConstructor() {
		DataEntry[] arr = new DataEntry[10];
		for (int i = 0; i < arr.length; ++i) {
			arr[i] = new DataEntry(DataEntryType.user, "1", 1, "");
		}
		for (int i = 0; i < arr.length - 1; ++i) {
			Assert.assertTrue(arr[i].getTimeStamp() < arr[i + 1].getTimeStamp());
		}
	}

	private static final int NUM_USERS = 10000;
	private static final int NUM_TOPICS = 10000;
	private static final int NUM_QUESTIONS = 10000;
	private static final int USER_NAME_LENGTH = 3;
	private static final int TOPIC_NAME_LENGTH = 5;
	private static final int QUESTION_LENGTH = 20;

	// private static final int NUM_QUERIES = 10000;
	private static final int NUM_QUERIES = 0;
	private static final int QUERY_STR_LENGTH = 5;
	private static final int QUERY_NUM_RESULTS = 20;

	private static final int NUM_WQUERIES = 0;
	// private static final int NUM_WQUERIES = 10000;
	private static final int BOOSTS_COUNT_MAX_NUM = 25;
	private static final int BOOSTS_MAX_SCORE = 100;
	private static final int NUM_DELS = 200;

	private static final int TOTAL_NUM = NUM_USERS + NUM_TOPICS + NUM_QUESTIONS
			+ NUM_QUERIES + NUM_WQUERIES + NUM_DELS;

	@Test
	public void generateTestData() throws IOException {

		String inFileName = "/home/bolei/Desktop/corpus.txt";
		String outFileName = "/home/bolei/Desktop/big_test_no_all_queries.txt";

		BufferedReader in = new BufferedReader(new FileReader(inFileName));
		PrintWriter out = new PrintWriter(outFileName);

		ArrayList<String> corpusList = new ArrayList<>();
		String line;
		while ((line = in.readLine()) != null) {
			corpusList.add(line);
		}
		out.println(TOTAL_NUM);
		ArrayList<String> idList = new ArrayList<>();
		// create add commands
		for (int i = 0; i < NUM_USERS; ++i) {
			String userName = getSentence(corpusList, USER_NAME_LENGTH);
			String uid = "u" + i;
			idList.add(uid);
			out.println(generateAddUser(uid, userName));
		}
		for (int i = 0; i < NUM_TOPICS; ++i) {
			String topicName = getSentence(corpusList, TOPIC_NAME_LENGTH);
			String tid = "t" + i;
			idList.add(tid);
			out.println(generateAddTopic(tid, topicName));
		}
		for (int i = 0; i < NUM_QUESTIONS; ++i) {
			String questionName = getSentence(corpusList, QUESTION_LENGTH);
			String qid = "q" + i;
			idList.add(qid);
			out.println(generateAddQuestion(qid, questionName));
		}

		for (int i = 0; i < NUM_QUERIES; ++i) {
			String queryStr = getSentence(corpusList, QUERY_STR_LENGTH);
			out.println(generateQuery(queryStr));
		}

		ArrayList<String> boostKeyList = new ArrayList<>(idList);
		boostKeyList.add("user");
		boostKeyList.add("topic");
		boostKeyList.add("question");
		boostKeyList.add("board");

		for (int i = 0; i < NUM_WQUERIES; ++i) {
			String queryStr = getSentence(corpusList, QUERY_STR_LENGTH);
			out.println(generateWQuery(queryStr, boostKeyList));
		}

		for (int i = 0; i < NUM_DELS; ++i) {
			String id = idList.get(rand.nextInt(idList.size()));
			out.println(String.format("DEL %s", id));
		}

		in.close();
		out.close();
	}

	private String generateWQuery(String queryStr,
			ArrayList<String> boostKeyList) {
		int num_boosts = rand.nextInt(BOOSTS_COUNT_MAX_NUM);
		return String.format("WQUERY %d %d %s %s",
				rand.nextInt(QUERY_NUM_RESULTS), num_boosts,
				getBoostString(num_boosts, boostKeyList), queryStr);
	}

	private String getBoostString(int num_boosts, ArrayList<String> boostKeyList) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < num_boosts; ++i) {
			if (i != 0) {
				sb.append(' ');
			}
			sb.append(boostKeyList.get(rand.nextInt(boostKeyList.size())));
			sb.append(':');
			sb.append(rand.nextFloat() * BOOSTS_MAX_SCORE);
		}
		return sb.toString();
	}

	private String generateQuery(String queryStr) {
		return String.format("QUERY %d %s", rand.nextInt(QUERY_NUM_RESULTS),
				queryStr);
	}

	private String generateAddQuestion(String qid, String questionName) {
		return String.format("ADD question %s %f %s", qid, rand.nextFloat(),
				questionName);
	}

	private String generateAddTopic(String tid, String topicName) {
		return String.format("ADD topic %s %f %s", tid, rand.nextFloat(),
				topicName);
	}

	private String generateAddUser(String id, String userName) {
		return String.format("ADD user %s 1.0 %s", id, userName);
	}

	private String getSentence(ArrayList<String> corpusList, int len) {
		int corpusSize = corpusList.size();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < len; ++i) {
			if (i != 0) {
				sb.append(' ');
			}
			String str = corpusList.get(rand.nextInt(corpusSize));
			sb.append(str.substring(0, rand.nextInt(str.length())));
		}
		return sb.toString();
	}

	@Test
	public void testSizeComparableSet() {
		TreeSet<SizeComparableSet<Integer>> cache = new TreeSet<>();
		SizeComparableSet<Integer> s1 = new SizeComparableSet<>();
		SizeComparableSet<Integer> s2 = new SizeComparableSet<>();
		Collections.addAll(s1, 1, 2, 3);
		Collections.addAll(s2, 3, 4, 5);

		cache.add(s1);
		
		Assert.assertFalse(cache.contains(s2));
		
		cache.add(s2);

		Assert.assertNotEquals(s1, s2);

		HashSet<Integer> resultSet = null;
		Iterator<SizeComparableSet<Integer>> it = cache.iterator();
		if (it.hasNext()) {
			resultSet = it.next();
		}

		while (it.hasNext()) {
			resultSet.retainAll(it.next());
		}
		System.out.println(resultSet);
	}
}
