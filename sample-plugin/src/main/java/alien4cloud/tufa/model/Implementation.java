package alien4cloud.tufa.model;

import java.util.ArrayList;
import java.util.List;

public class Implementation {
    private String implementationType;
    private int requiredResourceUnit;
    private List<Integer> computationRange;
    private List<Double> memoryRange;
    private List<Double> storageRange;
    private List<Double> bandwidthRange;
    private List<Integer> acceleratorRange;

    public Implementation() {
	this.implementationType = "";
	this.requiredResourceUnit = 0;
	this.computationRange = new ArrayList<Integer>();
	this.memoryRange = new ArrayList<Double>();
	this.storageRange = new ArrayList<Double>();
	this.bandwidthRange = new ArrayList<Double>();
	this.acceleratorRange = new ArrayList<Integer>();
    }

    /**
     * @return the implementationType
     */
    public String getImplementationType() {
	return implementationType;
    }

    /**
     * @param implementationType
     *            the implementationType to set
     */
    public void setImplementationType(String implementationType) {
	this.implementationType = implementationType;
    }

    /**
     * @return the requiredResourceUnit
     */
    public int getRequiredResourceUnit() {
	return requiredResourceUnit;
    }

    /**
     * @param requiredResourceUnit
     *            the requiredResourceUnit to set
     */
    public void setRequiredResourceUnit(int requiredResourceUnit) {
	this.requiredResourceUnit = requiredResourceUnit;
    }

    /**
     * @return the computationRange
     */
    public List<Integer> getComputationRange() {
	return computationRange;
    }

    /**
     * @param computationRange
     *            the computationRange to set
     */
    public void setComputationRange(List<Integer> computationRange) {
	this.computationRange = computationRange;
    }

    /**
     * @return the memoryRange
     */
    public List<Double> getMemoryRange() {
	return memoryRange;
    }

    /**
     * @param memoryRange
     *            the memoryRange to set
     */
    public void setMemoryRange(List<Double> memoryRange) {
	this.memoryRange = memoryRange;
    }

    /**
     * @return the storageRange
     */
    public List<Double> getStorageRange() {
	return storageRange;
    }

    /**
     * @param storageRange
     *            the storageRange to set
     */
    public void setStorageRange(List<Double> storageRange) {
	this.storageRange = storageRange;
    }

    /**
     * @return the bandwidthRange
     */
    public List<Double> getBandwidthRange() {
	return bandwidthRange;
    }

    /**
     * @param bandwidthRange
     *            the bandwidthRange to set
     */
    public void setBandwidthRange(List<Double> bandwidthRange) {
	this.bandwidthRange = bandwidthRange;
    }

    /**
     * @return the acceleratorRange
     */
    public List<Integer> getAcceleratorRange() {
	return acceleratorRange;
    }

    /**
     * @param acceleratorRange
     *            the acceleratorRange to set
     */
    public void setAcceleratorRange(List<Integer> acceleratorRange) {
	this.acceleratorRange = acceleratorRange;
    }

}