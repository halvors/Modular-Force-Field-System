package mffs.api.machine;

public abstract interface ForceField {
	public Projector getProjector();

	/**
	 * Weakens a force field block, destroying it temporarily and draining power from the projector.
	 * @param joules - Power to drain.
	 */
	public void weakenForceField(int joules);
}