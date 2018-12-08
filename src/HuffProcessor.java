import java.io.InputStream;
import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){	
		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root, out);
		writeCompressedBits(codings, in, out);
		out.close();
	}
	
	public int[] readForCounts(BitInputStream in) {
		int[] counts = new int[ALPH_SIZE + 1];
		while (true){
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			counts[val] += 1; 
		}
		counts[PSEUDO_EOF] = 1;
		return counts;
		
	}
	public HuffNode makeTreeFromCounts(int[] counts) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		for(int index = 0; index < counts.length; index++) {
			if (counts[index] > 0) {
				pq.add(new HuffNode(index,counts[index],null, null));
			} 
		}
		while (pq.size() > 1) {
		    HuffNode left = pq.remove();
		    HuffNode right = pq.remove();
		    HuffNode t = new HuffNode(0, left.myWeight + right.myWeight, left, right);
		    // create new HuffNode t with weight from
		    // left.weight+right.weight and left, right subtrees
		    pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;
	}
	public String[] makeCodingsFromTree(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE + 1];
	    codingHelper(root,"",encodings);
	    return encodings;
	    
	}
	
	public void codingHelper(HuffNode root, String path, String[] encodings) {
		if (root.myLeft == null && root.myRight == null) {
	        encodings[root.myValue] = path;
	        return;
		}
		else {
			codingHelper(root.myLeft, path + 0, encodings);
			codingHelper(root.myRight, path + 1, encodings);
		}

	}
	
	public void writeHeader(HuffNode root, BitOutputStream out) {
		if (root.myRight != null || root.myLeft != null) {
			out.writeBits(1, 0);
			writeHeader(root.myLeft, out);
			writeHeader(root.myRight, out);
		}
		if (root.myLeft == null && root.myRight == null) {
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD + 1, root.myValue);
			return;
		}
		
	}
	public void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		while (true){
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			String code = codings[val];
			out.writeBits(code.length(), Integer.parseInt(code,2));
			out.writeBits(BITS_PER_WORD, val);
		}
		
		String codeEOF = codings[PSEUDO_EOF];
		out.writeBits(codeEOF.length(), Integer.parseInt(codeEOF,2));
		
	}
	
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
	
		int bits = in.readBits(BITS_PER_INT);
		if (bits != HUFF_TREE) {
			throw new HuffException("illegal header starts with " + bits);
		}
//		
//		while (true){
//			int val = in.readBits(BITS_PER_WORD);
//			if (val == -1) break;
//			out.writeBits(BITS_PER_WORD, val);
//		}
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root, in, out);
		out.close();
	}
	
	public HuffNode readTreeHeader(BitInputStream in) {
		int bits = in.readBits(1);
//		if (bits != HUFF_TREE) throw new HuffException("illegal header starts with " + bits);
		if (bits == -1) throw new HuffException("reading bits failed " + bits);
		if (bits == 0) {
		    HuffNode left = readTreeHeader(in);
		    HuffNode right = readTreeHeader(in);
		    return new HuffNode(0,0,left,right);
		}
		else {
		    int value = in.readBits(BITS_PER_WORD + 1);
		    return new HuffNode(value,0,null,null);
		}
	}
	
	public void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode current = root; 
		while (true) {
			int bits = in.readBits(1);
			if (bits == -1) throw new HuffException("bad input, no PSEUDO_EOF");
			else { 
				if (bits == 0) current = current.myLeft;
				else current = current.myRight;
				if (current.myLeft == null && current.myRight == null) {
					if (current.myValue == PSEUDO_EOF) 
						break;   // out of loop
					else {
						// might be myValue.length
						out.writeBits(BITS_PER_WORD,current.myValue);
						current = root; // start back after leaf
					}
	           }
	       }
	   }
		

		
	}
}