import { ProgressEventDto } from '../types';

export function subscribeToBatchEvents(
  batchId: string,
  onProgress: (event: ProgressEventDto) => void,
  onComplete: (event: ProgressEventDto) => void,
  onError?: (err: any) => void
): () => void {
  const url = `/api/v1/upscale/batches/${batchId}/events`;
  const eventSource = new EventSource(url);

  eventSource.addEventListener('PROGRESS', (e: MessageEvent) => {
    try {
      const data: ProgressEventDto = JSON.parse(e.data);
      onProgress(data);
    } catch (err) {
      console.error('Failed to parse SSE PROGRESS event', err);
    }
  });

  eventSource.addEventListener('COMPLETED', (e: MessageEvent) => {
    try {
      const data: ProgressEventDto = JSON.parse(e.data);
      onComplete(data);
      eventSource.close();
    } catch (err) {
      console.error('Failed to parse SSE COMPLETED event', err);
    }
  });

  eventSource.onerror = (err) => {
    if (onError) onError(err);
  };

  return () => {
    eventSource.close();
  };
}
