/**
 * Copyright 2008 Andre Pfeiler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.twodividedbyzero.idea.findbugs.common.event;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.twodividedbyzero.idea.findbugs.common.event.Event.EventType;
import org.twodividedbyzero.idea.findbugs.common.event.filters.EventFilter;
import org.twodividedbyzero.idea.findbugs.common.event.types.EventManagerEvent;
import org.twodividedbyzero.idea.findbugs.common.event.types.EventManagerEventImpl;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;


/**
 * TODO: EventQueue
 * <p/>
 * $Date$
 *
 * @author Andre Pfeiler<andrep@twodividedbyzero.org>
 * @version $Revision$
 * @since 0.0.1
 */
@SuppressWarnings({"RawUseOfParameterizedType", "HardCodedStringLiteral"})
public class EventManagerImpl implements EventManager {

	private static final Logger LOGGER = Logger.getInstance(EventManager.class.getName());

	//protected LinkedBlockingQueue<Event> eventQueue = new LinkedBlockingQueue<Event>();
	private static final EventManager _instance = new EventManagerImpl();
	private final Map<EventType, Collection<MulticastEventFilter>> _eventFilters;


	private EventManagerImpl() {
		_eventFilters = new EnumMap<EventType, Collection<MulticastEventFilter>>(EventType.class);
		initialize();
	}


	public static EventManager getManager() {
		return getInstance();
	}


	public static EventManager getInstance() {
		return _instance;
	}


	@NotNull
	public static String getName() {
		return EventManager.NAME;
	}


	public final void initialize() {
		for (final EventType eventType : EventType.values()) {
			_eventFilters.put(eventType, new ConcurrentLinkedQueue<MulticastEventFilter>());
		}
	}


	@SuppressWarnings({"unchecked"})
	public void fireEvent(@NotNull final Event event) {
		if (_eventFilters.isEmpty()) {
			LOGGER.debug("No listeners registered.");
		}

		final Collection<MulticastEventFilter> multicastEventFilters = _eventFilters.get(event.getType());
		for (final MulticastEventFilter multicastEventFilter : multicastEventFilters) {
			if (multicastEventFilter.getEventFilter().acceptEvent(event)) {
				multicastEventFilter.getEventListener().onEvent(event);
				LOGGER.debug("fireEvent: " + event.toString() + " Listener{" + multicastEventFilter.getEventListener().toString() + "}");
			} else {
				LOGGER.debug("No listeners accepted eventType [" + event.getType() + "] registered. Event was: [" + event.toString() + "]");
			}
		}
	}


	public boolean hasListener(@NotNull final EventFilter<?> eventFilter, @NotNull final EventListener<?> listener) {
		if (!hasListeners(eventFilter.getEventType())) {
			return false;
		}

		int count = 0;
		final Collection<MulticastEventFilter> multicastEventFilters = _eventFilters.get(eventFilter.getEventType());

		for (final MulticastEventFilter multicastEventFilter : multicastEventFilters) {
			if (multicastEventFilter.getEventFilter().getProjectName().equals(eventFilter.getProjectName()) && multicastEventFilter.getEventListener().equals(listener)) {
				count++;
			}
		}
		return count > 0;
	}


	public boolean hasListeners(final Event event) {
		return hasListeners(event.getType());
	}


	public boolean hasListeners(final EventType type) {
		return !_eventFilters.get(type).isEmpty();
	}


	public <T extends Event> void addEventListener(@NotNull final EventFilter<T> eventFilter, @NotNull final EventListener<T> listener) {
		if (!hasListener(eventFilter, listener)) {
			_eventFilters.get(eventFilter.getEventType()).add(new MulticastEventFilter(eventFilter, listener));
			fireEvent(new EventManagerEventImpl(EventManagerEvent.Operation.LISTENER_ADDED));
			LOGGER.debug("Registered listener [" + listener + "] for EventFilter was: [" + eventFilter + "]");
		} else {
			LOGGER.debug("Listener already registered [" + listener + "] for EventType [" + eventFilter.getEventType() + "] and project [" + eventFilter.getProjectName() + "]");
		}
	}


	public void removeEventListener(@NotNull final EventListener<?> listener) {
		for (final Collection<MulticastEventFilter> multicastEventFilters : _eventFilters.values()) {
			for (final Iterator<MulticastEventFilter> iter = multicastEventFilters.iterator(); iter.hasNext();) {
				final MulticastEventFilter multicastEventFilter = iter.next();

				if (multicastEventFilter.getEventListener().equals(listener)) {
					iter.remove();
					fireEvent(new EventManagerEventImpl(EventManagerEvent.Operation.LISTENER_REMOVED));
					LOGGER.debug("Unregistered listener [" + listener + "].");
				}
			}
		}
	}


	public void removeEventListener(@NotNull final Project project) {

		for (final Collection<MulticastEventFilter> multicastEventFilters : _eventFilters.values()) {
			for (final Iterator<MulticastEventFilter> iter = multicastEventFilters.iterator(); iter.hasNext();) {
				final MulticastEventFilter multicastEventFilter = iter.next();

				if (multicastEventFilter.getEventFilter().acceptProject(project.getName())) {
					iter.remove();
					fireEvent(new EventManagerEventImpl(EventManagerEvent.Operation.LISTENER_REMOVED));
					LOGGER.debug("Unregistered listener [" + multicastEventFilter + "]. project=" + project.getName());
				}
			}
		}
	}


	public void removeListener(final EventType eventType) {
		_eventFilters.get(eventType).clear();
	}


	public void removeAllListeners() {
		_eventFilters.clear();
	}


	public static Class<?> getManagerInterface() {
		return EventManager.class;
	}


	public Set<EventListener<?>> getListeners() {
		final Set<EventListener<?>> result = new HashSet<EventListener<?>>();
		for (final Collection<MulticastEventFilter> multicastEventFilters : _eventFilters.values()) {
			for (final MulticastEventFilter multicastEventFilter : multicastEventFilters) {
				result.add(multicastEventFilter.getEventListener());
			}
		}
		return result;
	}


	private static class MulticastEventFilter {

		private final EventListener<? extends Event> _eventListener;
		private final EventFilter<? extends Event> _eventFilter;


		MulticastEventFilter(@NotNull final EventFilter<? extends Event> eventFilter, @NotNull final EventListener<? extends Event> eventListener) {
			_eventListener = eventListener;
			_eventFilter = eventFilter;
		}


		public EventListener getEventListener() {
			return _eventListener;
		}


		public EventFilter getEventFilter() {
			return _eventFilter;
		}


		@Override
		public boolean equals(final Object obj) {
			return obj instanceof MulticastEventFilter && ((MulticastEventFilter) obj).getEventListener().equals(_eventListener);
		}


		@Override
		public int hashCode() {
			int result = _eventListener.hashCode();
			result = 31 * result + _eventFilter.hashCode();
			return result;
		}


		@Override
		public String toString() {
			return "MulticastEventFilter{" + "_eventListener=" + _eventListener + ", _eventType=" + _eventFilter.getEventType() + '}';
		}
	}


}
