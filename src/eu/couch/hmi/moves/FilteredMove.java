package eu.couch.hmi.moves;


public class FilteredMove extends Move implements Comparable<FilteredMove> {
	
	public float score = 0;
	public FilteredMove() {
	}

	public FilteredMove(Move copy) {
		super(copy);
	}

	@Override
	public int compareTo(FilteredMove o) {
		if (o.score < this.score) 
			return -1;
		if (o.score > this.score)
			return 1;
		return 0;
	}

	
}
