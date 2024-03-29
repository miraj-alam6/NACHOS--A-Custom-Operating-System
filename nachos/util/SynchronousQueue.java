package nachos.util;

import nachos.kernel.Nachos;
import nachos.kernel.threads.*;
import nachos.Debug;
/**
 * This class is patterned after the SynchronousQueue class
 * in the java.util.concurrent package.
 *
 * A SynchronousQueue has no capacity: each insert operation
 * must wait for a corresponding remove operation by another
 * thread, and vice versa.  A thread trying to insert an object
 * enters a queue with other such threads, where it waits to
 * be matched up with a thread trying to remove an object.
 * Similarly, a thread trying to remove an object enters a
 * queue with other such threads, where it waits to be matched
 * up with a thread trying to insert an object.
 * If there is at least one thread waiting to insert and one
 * waiting to remove, the first thread in the insertion queue
 * is matched up with the first thread in the removal queue
 * and both threads are allowed to proceed, after transferring
 * the object being inserted to the thread trying to remove it.
 * At any given time, the <EM>head</EM> of the queue is the
 * object that the first thread on the insertion queue is trying
 * to insert, if there is any such thread, otherwise the head of
 * the queue is null.
 */

public class SynchronousQueue<T> implements Queue<T> {
    
    Semaphore objectLock;
    Semaphore dataAvail;
    Semaphore consumeAvail;
    Semaphore producerLock;
    Semaphore consumerLock;
    Semaphore offerLock;
    Semaphore pollLock;
    Semaphore offerCalloutLock;
    Semaphore pollCalloutLock;
    boolean tryingToPut = false;
    boolean tryingToTake = false;
//    boolean gaveDataThruOffer = false; //If you give data through offer, do not
    T object;
    SpinLock sl;
    /**
     * Initialize a new SynchronousQueue object.
     */
    public SynchronousQueue() {
	sl = new SpinLock("SynchronousQueue mutex");
	
	objectLock = new Semaphore("objectLock", 1);
	dataAvail = new Semaphore("dataAvail",0);
	consumeAvail = new Semaphore("consumeAvail",0);
	producerLock = new Semaphore("producerLock", 1);
	consumerLock = new Semaphore("consumerLock", 1);
	offerLock = new Semaphore("offerLock", 1);
	pollLock = new Semaphore("pollLock", 1);
	offerCalloutLock = new Semaphore("offerLock", 1);
	pollCalloutLock = new Semaphore("pollLock", 1);
    }

    /**
     * Adds the specified object to this queue,
     * waiting if necessary for another thread to remove it.
     *
     * @param obj The object to add.
     */
    public boolean put(T obj) { 
	producerLock.P();
	tryingToPut = true;
	consumeAvail.P();
	object = obj;
	dataAvail.V();
	producerLock.V();
	return true;
    }

    /**
     * Retrieves and removes the head of this queue,
     * waiting if necessary for another thread to insert it.
     *
     * @return the head of this queue.
     */
    public T take() {
	consumerLock.P();
	Debug.println('+',"set trying to take to true");
	tryingToTake = true;
	consumeAvail.V();
	dataAvail.P();
	T returnObj = object;
	object = null;	
	consumerLock.V();
	return returnObj;
    }

    /**
     * Adds an element to this queue, if there is a thread currently
     * waiting to remove it, otherwise returns immediately.
     * 
     * @param e  The element to add.
     * @return  true if the element was successfully added, false if the element
     * was not added.
     */
    @Override
    public boolean offer(T e) {
	offerLock.P(); //to prevent concurrency messing with shared data
	Debug.println('m',"Reached here offer");
	if(tryingToTake){
	  //  Debug.println('+',"Reached here1");
	    object = e;
	    consumeAvail.P(); //there will be no waiting here because, only way you got in here is if
	    //the semaphore already has a value of 1
	    dataAvail.V();
	    offerLock.V(); //to prevent concurrency messing with shared data
	    tryingToTake = false;
	    return true;
	}
	//Debug.println('+',"Reached here2");
	offerLock.V(); //to prevent concurrency messing with shared data
	return false;
    }
    
    /**
     * Retrieves and removes the head of this queue, if another thread
     * is currently making an element available.
     * 
     * @return  the head of this queue, or null if no element is available.
     */
    @Override
    public T poll() { 
	pollLock.P();
	Debug.println('m', "trying" + tryingToPut);
	if(tryingToPut){
	    
	    consumeAvail.V();
	    dataAvail.P();//there is effectively no waiting here because, only way we got here is if  consumeAvail.P()
	    //was waiting in the put function, and just as .P() ends in the put function it will carry out
	    //dataAvail.V() thus, even though I wrote dataAvail.P() here, there is never any chance that we will
	    //idly waiting for it, thus it is a poll function, and not a take function
	    tryingToPut = false;
	    pollLock.V();
	    return object;
	}
	pollLock.V();
	return null;
    }
    
    /**
     * Always returns null.
     *
     * @return  null
     */
    @Override
    public T peek() { return null; }
    
    /**
     * Always returns true.
     * 
     * @return true
     */
    @Override
    public boolean isEmpty() { return true; }

    // The following methods are to be implemented for the second
    // part of the assignment.

    /**
     * Adds an element to this queue, waiting up to the specified
     * timeout for a thread to be ready to remove it.
     * 
     * @param e  The element to add.
     * @param timeout  The length of time (in "ticks") to wait for a
     * thread to be ready to remove the element, before giving up and
     * returning false.
     * @return  true if the element was successfully added, false if the element
     * was not added.
     */
    public boolean offer(T e, int timeout) 
    {
	
	producerLock.P();
	tryingToPut = true;
//	offerCalloutLock.P(); //probably delete this

	//Can't simply use booleans for this next part because things need to be effectively final,
	//so instead I will just make two objects that each hold a boolean.
	//boolean offerSucceeded = false;
	final BooleanHolder offerSucceededObject = new BooleanHolder();
	//boolean offerFailed = false
	final BooleanHolder offerFailedObject = new BooleanHolder();
	

	
	Runnable scheduledCallout = new Runnable(){
		    @Override
		    public void run() {
			if(offerSucceededObject.b){
			    return;
			}
			//offerFailed = false; //this isn't possible for effectively final functions
			offerFailedObject.b = true; //this is how to bypass effectively final
			consumeAvail.V();

			return;
		    }
	};
	
	Nachos.scheduler.getCalloutF().schedule(scheduledCallout, timeout);
	if(tryingToTake)
	{
	    dataAvail.V();
	}
	consumeAvail.P();
	//You get here, either if a consumerdoes V() or if the callout is executed.
	//If the callout is executed, then offerFailed will have been set to true,
	//if you got her because of V() and you see that offerFailed is not true, immediately
	//set offerSucceeded to true, so that any callouts that happen will realize that they should not
	//set offerFailed to true and that they should do a consumeAvail.V(), since the consumeAvail.V() is done
	//by this function
	//if(offerFailed){ //can't use offerFailed because its effectively final property does not let it be changed in run
	if(offerFailedObject.b){
//	    offerCalloutLock.V(); //get rid of this
	    tryingToPut = false;
	    producerLock.V();
	    return false;
	}
	else{
	    offerSucceededObject.b = true;
	    object = e; 
	    tryingToPut = false;
	    dataAvail.V();
//	    offerCalloutLock.V(); //get rid of this
	    producerLock.V();
	    return true;
	}
    }
    
    /**
     * Retrieves and removes the head of this queue, waiting up to the
     * specified timeout for a thread to make an element available.
     * 
     * @param timeout  The length of time (in "ticks") to wait for a
     * thread to make an element available, before giving up and returning
     * true.
     * @return  the head of this queue, or null if no element is available.
     */
    public T poll(int timeout) 
    {

	//pollCalloutLock.P(); 
	consumerLock.P();
	tryingToTake = true;
	final BooleanHolder pollSucceededObject = new BooleanHolder();
	final BooleanHolder pollFailedObject = new BooleanHolder();
	
	Runnable scheduledCallout = new Runnable(){
		    @Override
		    public void run() {
			if(pollSucceededObject.b){
			    return;
			}
			//offerFailed = false; //this isn't possible for effectively final functions
			pollFailedObject.b = true; //this is how to bypass effectively final
			dataAvail.V();

			return;
		    }
	};
    
    Nachos.scheduler.getCalloutF().schedule(scheduledCallout, timeout);
    //Debug.println('m',"set trying to take to true");
    if(tryingToPut)
    {
	consumeAvail.V();
    }
    dataAvail.P(); //will get this marble back either from the callout thus indicating failure or get it
    	 	   //from put, indicating  success
    T returnObj = object;
    if(pollFailedObject.b){
	//    offerCalloutLock.V();
	tryingToTake = false;
	consumerLock.V();
	return null;
	}
    else{
	    tryingToTake = false;
	    consumeAvail.V();
	  //  offerCalloutLock.V();
	    consumerLock.V();
	    return object;
	}
    }

    private static class BooleanHolder{
	public boolean b;
	public BooleanHolder(){
	    b = false;
	}
    }
  
}

