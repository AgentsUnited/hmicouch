package eu.couch.hmi.moves;

public interface IMoveDistributor {

	public void registerMoveListener(IMoveListener listener);
	public void registerMoveFilter(IMoveFilter filter);
	
}
