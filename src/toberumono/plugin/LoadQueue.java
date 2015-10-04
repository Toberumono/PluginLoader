package toberumono.plugin;

import java.util.Collection;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Synchronizes access to a {@link Queue} on a {@link ReadWriteLock}
 * 
 * @author Joshua Lipstone
 * @param <T>
 *            the type of element to store
 */
public class LoadQueue<T> implements Queue<T> {
	private final ReadWriteLock lock;
	private final Queue<T> back;
	
	/**
	 * Convenience constructor for {@link #LoadQueue(ReadWriteLock, Queue) LoadQueue(null, null)}
	 */
	public LoadQueue() {
		this(null);
	}
	
	/**
	 * Convenience constructor for {@link #LoadQueue(ReadWriteLock, Queue) ClassLoadQueue(lock, null)}
	 * 
	 * @param lock
	 *            the {@link ReadWriteLock} to be used. If this is {@code null}, then a {@link ReentrantReadWriteLock} is
	 *            created and used
	 */
	public LoadQueue(ReadWriteLock lock) {
		this(lock, null);
	}
	
	/**
	 * @param lock
	 *            the {@link ReadWriteLock} to be used. If this is {@code null}, then a {@link ReentrantReadWriteLock} is
	 *            created and used
	 * @param queue
	 *            the {@link Queue} to be used. If this is {@code null}, then a {@link PriorityQueue} is created and used
	 */
	public LoadQueue(ReadWriteLock lock, Queue<T> queue) {
		this.lock = (lock == null ? new ReentrantReadWriteLock() : lock);
		this.back = (queue == null ? new PriorityQueue<>() : queue);
	}
	
	@Override
	public int size() {
		try {
			lock.readLock().lock();
			return back.size();
		}
		finally {
			lock.readLock().unlock();
		}
	}
	
	@Override
	public boolean isEmpty() {
		try {
			lock.readLock().lock();
			return back.isEmpty();
		}
		finally {
			lock.readLock().unlock();
		}
	}
	
	@Override
	public boolean contains(Object o) {
		try {
			lock.readLock().lock();
			return back.contains(o);
		}
		finally {
			lock.readLock().unlock();
		}
	}
	
	@Override
	public Iterator<T> iterator() {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public Object[] toArray() {
		try {
			lock.readLock().lock();
			return back.toArray();
		}
		finally {
			lock.readLock().unlock();
		}
	}
	
	@Override
	public <R> R[] toArray(R[] a) {
		try {
			lock.readLock().lock();
			return back.toArray(a);
		}
		finally {
			lock.readLock().unlock();
		}
	}
	
	@Override
	public boolean remove(Object o) {
		try {
			lock.writeLock().lock();
			return back.remove(o);
		}
		finally {
			lock.writeLock().unlock();
		}
	}
	
	@Override
	public boolean containsAll(Collection<?> c) {
		try {
			lock.readLock().lock();
			return back.containsAll(c);
		}
		finally {
			lock.readLock().unlock();
		}
	}
	
	@Override
	public boolean addAll(Collection<? extends T> c) {
		try {
			lock.writeLock().lock();
			return back.addAll(c);
		}
		finally {
			lock.writeLock().unlock();
		}
	}
	
	@Override
	public boolean removeAll(Collection<?> c) {
		try {
			lock.writeLock().lock();
			return back.removeAll(c);
		}
		finally {
			lock.writeLock().unlock();
		}
	}
	
	@Override
	public boolean retainAll(Collection<?> c) {
		try {
			lock.writeLock().lock();
			return back.retainAll(c);
		}
		finally {
			lock.writeLock().unlock();
		}
	}
	
	@Override
	public void clear() {
		try {
			lock.writeLock().lock();
			back.clear();
		}
		finally {
			lock.writeLock().unlock();
		}
	}
	
	@Override
	public boolean add(T e) {
		try {
			lock.writeLock().lock();
			return back.add(e);
		}
		finally {
			lock.writeLock().unlock();
		}
	}
	
	@Override
	public boolean offer(T e) {
		try {
			lock.writeLock().lock();
			return back.offer(e);
		}
		finally {
			lock.writeLock().unlock();
		}
	}
	
	@Override
	public T remove() {
		try {
			lock.writeLock().lock();
			return back.remove();
		}
		finally {
			lock.writeLock().unlock();
		}
	}
	
	@Override
	public T poll() {
		try {
			lock.writeLock().lock();
			return back.poll();
		}
		finally {
			lock.writeLock().unlock();
		}
	}
	
	@Override
	public T element() {
		try {
			lock.readLock().lock();
			return back.element();
		}
		finally {
			lock.readLock().unlock();
		}
	}
	
	@Override
	public T peek() {
		try {
			lock.readLock().lock();
			return back.peek();
		}
		finally {
			lock.readLock().unlock();
		}
	}
	
}
