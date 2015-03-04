/**
 * http://www.quora.com/about/challenges
 * 
 * Typeahead Search
 * 
 * @author bolei
 * 
 */

package quorachallenge;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TypeaheadSearch {

	private QuoraIndexer indexer = new QuoraIndexer();

	private static final boolean IS_BENCHMARK_ON = true;

	private static final String WHITE_SPACE_REGEX = "\\s+";
	private static final String ADD_BODY_REGEX = "(\\S+)\\s+(\\S+)\\s+(\\S+)\\s*(.*)";
	private static final String QUERY_BODY_REGEX = "(\\d+)\\s*(.*)";

	private static final Pattern WHITE_SPACE_PATTERN = Pattern
			.compile(WHITE_SPACE_REGEX);
	private static final Pattern ADD_BODY_PATTERN = Pattern
			.compile(ADD_BODY_REGEX);
	private static final Pattern QUERY_BODY_PATTERN = Pattern
			.compile(QUERY_BODY_REGEX);

	private static final HashSet<String> TYPE_NAME_SET = new HashSet<>();
	static {
		Collections.addAll(TYPE_NAME_SET, "user", "topic", "question", "board");
	}

	public void executeInput(String input, PrintStream output) {
		Matcher matcher = WHITE_SPACE_PATTERN.matcher(input);
		matcher.find();
		int iFirstSpace = matcher.start();
		String command = input.substring(0, iFirstSpace);
		String body = input.substring(iFirstSpace + 1);
		if (command.equals("ADD")) {
			executeAdd(body);
		} else if (command.equals("DEL")) {
			executeDel(body);
		} else if (command.equals("QUERY")) {
			output.println(executeQuery(body));
		} else if (command.equals("WQUERY")) {
			output.println(executeWquery(body));
		} else {
			output.println("======UNKNOWN COMMAND: " + command + "========");
		}
	}

	private String executeWquery(String body) {

		Matcher matcher = WHITE_SPACE_PATTERN.matcher(body);
		int index, oldi = 0;

		matcher.find();
		index = matcher.start();
		String numResultStr = body.substring(oldi, index);
		int numResult = Integer.parseInt(numResultStr);
		oldi = matcher.end();

		matcher.find();
		index = matcher.start();
		String numBoostStr = body.substring(oldi, index);
		int numBoost = Integer.parseInt(numBoostStr);
		oldi = matcher.end();

		HashMap<String, Float> boosts = new HashMap<>();

		for (int i = 0; i < numBoost; ++i) {
			boolean found = matcher.find();
			if (found == true) {
				index = matcher.start();
				String boostStr = body.substring(oldi, index);

				String[] strArr = boostStr.split(":");
				boosts.put(strArr[0], Float.parseFloat(strArr[1]));
				oldi = matcher.end();
			} else {
				index = body.length();
				String boostStr = body.substring(oldi, index);
				String[] strArr = boostStr.split(":");
				boosts.put(strArr[0], Float.parseFloat(strArr[1]));
				oldi = body.length();
			}
		}

		String query = body.substring(oldi);

		return executeWquery(numResult, boosts, query);
	}

	private String executeQuery(String body) {
		Matcher matcher = QUERY_BODY_PATTERN.matcher(body);
		matcher.matches();
		String numStr = matcher.group(1);
		String query = matcher.group(2);
		return executeWquery(Integer.parseInt(numStr), null, query);
	}

	private void executeAdd(String entryStr) {
		DataEntry entry = parseAddBody(entryStr);
		indexer.add(entry);
	}

	private void executeDel(String id) {
		indexer.delete(id);
	}

	private String executeWquery(int numResult, HashMap<String, Float> boosts,
			String query) {

		BenchMark bm = new BenchMark(IS_BENCHMARK_ON);

		if (query.isEmpty()) {
			return "";
		}

		bm.reset();
		PriorityQueue<SizeComparableSet<Integer>> cache = new PriorityQueue<>(
				20);
		for (String keyword : query.split("\\s+")) {
			if (keyword.isEmpty()) {
				continue;
			}
			Integer tokenId = TokenDictionary.lookupOnly(keyword);
			if (tokenId == null) {
				return "";
			}
			SizeComparableSet<Integer> tmpSet = indexer.query(tokenId);
			if (tmpSet == null || tmpSet.isEmpty()) {
				return "";
			}
			cache.add(tmpSet);
		}
		bm.measure("collect cached sets", true);

		HashSet<Integer> resultSet = null;
		Iterator<SizeComparableSet<Integer>> it = cache.iterator();
		if (it.hasNext()) {
			resultSet = it.next();
		}

		boolean useRetainAll = false;
		while (it.hasNext()) {
			if (useRetainAll == false) {
				// create a new set to hold the intersection
				resultSet = hashSetIntersection(resultSet, it.next());
				useRetainAll = true;
			} else {
				// operate on the same set
				resultSet.retainAll(it.next());
			}
			if (resultSet.isEmpty()) {
				return "";
			}
		}
		bm.measure("intersection", true);

		Collection<ResultDataEntry> descSortedResultColl = boostAndSort(
				resultSet, boosts);
		bm.measure("boost and sort", true);

		StringBuilder sb = new StringBuilder();

		Iterator<ResultDataEntry> descIt = descSortedResultColl.iterator();
		for (int i = 0; i < numResult && descIt.hasNext(); ++i) {
			if (i != 0) {
				sb.append(' ');
			}
			sb.append(descIt.next().getEntry().getId());
		}
		bm.measure("generate String", false);
		return sb.toString();
	}

	//
	// private Collection<ResultDataEntry> boostAndSort(
	// HashSet<Integer> resultSet, HashMap<String, Float> boosts) {
	// TreeSet<ResultDataEntry> sortedResultSet = new TreeSet<>(
	// Collections.reverseOrder());
	// for (Integer eIdInt : resultSet) {
	// DataEntry entry = indexer.lookupByid(eIdInt);
	// ResultDataEntry rDataEntry = new ResultDataEntry(entry,
	// entry.getScore());
	// String typeStr = entry.getType().toString();
	// String idStr = entry.getId();
	// if (boosts != null) {
	// if (boosts.containsKey(typeStr)) {
	// rDataEntry.boost(boosts.get(typeStr));
	// } else if (boosts.containsKey(idStr)) {
	// rDataEntry.boost(boosts.get(idStr));
	// }
	// }
	// sortedResultSet.add(rDataEntry);
	// }
	// return sortedResultSet;
	// }

	private Collection<ResultDataEntry> boostAndSort(
			HashSet<Integer> resultSet, HashMap<String, Float> boosts) {
		ArrayList<ResultDataEntry> resultArr = new ArrayList<>(resultSet.size());
		for (Integer eIdInt : resultSet) {
			DataEntry entry = indexer.lookupByid(eIdInt);
			ResultDataEntry rDataEntry = new ResultDataEntry(entry,
					entry.getScore());
			String typeStr = entry.getType().toString();
			String idStr = entry.getId();
			if (boosts != null) {
				if (boosts.containsKey(typeStr)) {
					rDataEntry.boost(boosts.get(typeStr));
				} else if (boosts.containsKey(idStr)) {
					rDataEntry.boost(boosts.get(idStr));
				}
			}
			resultArr.add(rDataEntry);
		}
		PriorityQueue<ResultDataEntry> sorted = new PriorityQueue<>(
				resultArr.size(), Collections.reverseOrder());
		sorted.addAll(resultArr);
		return sorted;
	}

	// O(A) where A is number of elements in smaller
	private HashSet<Integer> hashSetIntersection(HashSet<Integer> smaller,
			SizeComparableSet<Integer> larger) {
		HashSet<Integer> intersection = new HashSet<>();
		for (int i : smaller) {
			if (larger.contains(i)) {
				intersection.add(i);
			}
		}
		return intersection;
	}

	private DataEntry parseAddBody(String entryStr) {
		// parse: user u1 1.0 Adam D’Angelo
		Matcher matcher = ADD_BODY_PATTERN.matcher(entryStr);
		matcher.matches();
		String typeStr = matcher.group(1);
		String id = matcher.group(2);
		String scoreStr = matcher.group(3);
		String data = matcher.group(4);
		return new DataEntry(DataEntryType.valueOf(typeStr), id,
				Float.parseFloat(scoreStr), data);
	}

	private ArrayList<String> getInputList(InputStream is) {
		ArrayList<String> inputList = new ArrayList<>();
		BufferedReader in = null;
		try {
			in = new BufferedReader(new InputStreamReader(is));
			String nStr = in.readLine();
			int n = Integer.parseInt(nStr);
			for (int i = 0; i < n; ++i) {
				inputList.add(in.readLine());
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return inputList;
	}

	public static void main(String[] args) throws FileNotFoundException {

		BenchMark bm = new BenchMark(IS_BENCHMARK_ON);
		InputStream is = System.in;

		if (args.length > 0) {
			File f = new File(args[0]);
			is = new FileInputStream(f);
		}

		TypeaheadSearch qs = new TypeaheadSearch();
		ArrayList<String> inputList = qs.getInputList(is);
		for (String input : inputList) {
			qs.executeInput(input, System.out);
		}
		bm.measure("main", false);
	}
}

class QuoraIndexer {
	private HashMap<Integer, DataEntry> dataStore = new HashMap<>();

	// key_id -> set_entry_id
	private HashMap<Integer, SizeComparableSet<Integer>> invertedList = new HashMap<>();

	public void add(DataEntry entry) {
		int eIdInt = TokenDictionary.lookupOrAdd(entry.getId());
		dataStore.put(eIdInt, entry);
		for (Integer tokenId : entry.getTokenSet()) {
			if (invertedList.containsKey(tokenId) == false) {
				invertedList.put(tokenId, new SizeComparableSet<Integer>());
			}
			invertedList.get(tokenId).add(eIdInt);
		}
	}

	public void delete(String id) {
		int eIdInt = TokenDictionary.lookupOrAdd(id);
		DataEntry entry = dataStore.remove(eIdInt);
		if (entry == null) {
			return;
		}
		for (Integer tokenId : entry.getTokenSet()) {
			invertedList.get(tokenId).remove(eIdInt);
		}
	}

	public SizeComparableSet<Integer> query(int tokId) {
		return invertedList.get(tokId);
	}

	public DataEntry lookupByid(Integer eIdInt) {
		return dataStore.get(eIdInt);
	}
}

class SizeComparableSet<T> extends HashSet<T> implements Comparable<HashSet<T>> {

	/**
	 * default
	 */
	private static final long serialVersionUID = 1L;

	public SizeComparableSet() {
	}

	public SizeComparableSet(Collection<T> col) {
		super(col);
	}

	@Override
	public int compareTo(HashSet<T> o) {
		if (size() < o.size()) {
			return -1;
		} else if (size() == o.size()) {
			return 0;
		} else {
			return 1;
		}
	}

}

class DataEntry {

	private DataEntryType type;
	private String id;
	private float score;
	private String data;
	private HashSet<Integer> tokenSet;
	private long timeStamp;

	public DataEntryType getType() {
		return type;
	}

	public String getId() {
		return id;
	}

	public float getScore() {
		return score;
	}

	public String getData() {
		return data;
	}

	public DataEntry(DataEntryType type, String id, float score, String data) {
		this.type = type;
		this.id = id;
		this.score = score;
		this.data = data;
		this.tokenSet = tokenize(data);
		this.timeStamp = System.nanoTime();
	}

	public static HashSet<Integer> tokenize(String str) {
		HashSet<Integer> tokIdSet = new HashSet<>();
		for (String tok : str.split("\\s+")) {
			for (int i = 0; i < tok.length(); ++i) {
				String prefix = tok.substring(0, i + 1);
				tokIdSet.add(TokenDictionary.lookupOrAdd(prefix));
			}
		}
		return tokIdSet;
	}

	public HashSet<Integer> getTokenSet() {
		return tokenSet;
	}

	public long getTimeStamp() {
		return timeStamp;
	}

}

class ResultDataEntry implements Comparable<ResultDataEntry> {
	private DataEntry entry;
	private float fScore;

	public ResultDataEntry(DataEntry entry, float fScore) {
		this.entry = entry;
		this.fScore = fScore;
	}

	public float getfScore() {
		return fScore;
	}

	public void boost(float boostFactor) {
		this.fScore *= boostFactor;
	}

	public DataEntry getEntry() {
		return entry;
	}

	@Override
	public int compareTo(ResultDataEntry o) {
		if (o.getfScore() == getfScore()) {
			// tie is broken by added time
			if (entry.getTimeStamp() < o.entry.getTimeStamp()) {
				return -1;
			} else {
				return 1;
			}
		}
		if (getfScore() < o.getfScore()) {
			return -1;
		} else {
			return 1;
		}
	}
}

enum DataEntryType {
	user, topic, question, board
}

class TokenDictionary {
	private static int counter = 0;
	private static HashMap<String, Integer> dict = new HashMap<>();

	// private static ArrayList<String> invertLookup = new ArrayList<>();

	public static int lookupOrAdd(String token) {
		String myTok = token.toLowerCase();
		if (dict.containsKey(myTok) == false) {
			dict.put(myTok, counter++);
		}
		return dict.get(myTok);
	}

	public static Integer lookupOnly(String token) {
		return dict.get(token.toLowerCase());
	}

	public static int getDictSize() {
		return dict.size();
	}

	public static void reset() {
		counter = 0;
		dict.clear();
	}

}

class BenchMark {

	private boolean on;
	private long timeStamp;
	private String padding = "";

	public BenchMark(boolean b) {
		on = b;
		if (on) {
			System.out.println(padding + "=========");
			timeStamp = System.nanoTime();
		}
	}

	public void measure(String msg, boolean reset) {
		if (on) {
			long ts = System.nanoTime();
			System.out.println(padding + (ts - timeStamp) + "\t\t: " + msg);
			if (reset) {
				timeStamp = System.nanoTime();
			}
		}
	}

	public void reset() {
		if (on) {
			timeStamp = System.nanoTime();
		}
	}

	public void message(String message) {
		if (on) {
			System.out.println(padding + "===" + message);
		}
	}

}