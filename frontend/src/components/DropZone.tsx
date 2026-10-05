import React, { useRef, useState } from 'react';
import { UploadCloud, Image as ImageIcon, Trash2, X, Plus } from 'lucide-react';
import { UploadFileItem } from '../types';

interface DropZoneProps {
  stagedFiles: UploadFileItem[];
  onAddFiles: (items: UploadFileItem[]) => void;
  onRemoveFile: (id: string) => void;
  onClearAll: () => void;
  disabled?: boolean;
}

export const DropZone: React.FC<DropZoneProps> = ({
  stagedFiles,
  onAddFiles,
  onRemoveFile,
  onClearAll,
  disabled = false,
}) => {
  const [isDragOver, setIsDragOver] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleFiles = (files: FileList | null) => {
    if (!files || files.length === 0) return;

    const newItems: UploadFileItem[] = [];
    const validExtensions = ['jpg', 'jpeg', 'png', 'webp'];

    Array.from(files).forEach((file) => {
      const ext = file.name.split('.').pop()?.toLowerCase() || '';
      if (!validExtensions.includes(ext)) {
        return;
      }

      const id = `${file.name}_${file.size}_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`;
      const previewUrl = URL.createObjectURL(file);

      // Async measure dimensions
      const img = new Image();
      img.src = previewUrl;
      img.onload = () => {
        const mp = Number(((img.naturalWidth * img.naturalHeight) / 1_000_000).toFixed(2));
        item.width = img.naturalWidth;
        item.height = img.naturalHeight;
        item.megapixels = mp;
      };

      const item: UploadFileItem = {
        id,
        file,
        previewUrl,
        width: null,
        height: null,
        megapixels: null,
        size: file.size,
        format: ext.toUpperCase(),
        status: 'PENDING',
      };

      newItems.push(item);
    });

    onAddFiles(newItems);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragOver(false);
    if (disabled) return;
    handleFiles(e.dataTransfer.files);
  };

  return (
    <div className="space-y-4">
      {/* Drag & Drop Hero Box */}
      <div
        onDragOver={(e) => {
          e.preventDefault();
          if (!disabled) setIsDragOver(true);
        }}
        onDragLeave={() => setIsDragOver(false)}
        onDrop={handleDrop}
        onClick={() => !disabled && fileInputRef.current?.click()}
        className={`relative border-2 border-dashed rounded-3xl p-8 sm:p-12 text-center cursor-pointer transition-all duration-300 ${
          isDragOver
            ? 'border-cyan-400 bg-cyan-950/30 scale-[1.005] shadow-glow-cyan'
            : 'border-white/15 bg-white/[0.02] hover:border-cyan-500/50 hover:bg-white/[0.04]'
        } ${disabled ? 'opacity-50 cursor-not-allowed' : ''}`}
      >
        <input
          ref={fileInputRef}
          type="file"
          multiple
          accept=".jpg,.jpeg,.png,.webp"
          className="hidden"
          onChange={(e) => handleFiles(e.target.files)}
        />

        <div className="max-w-md mx-auto flex flex-col items-center gap-3">
          <div className="w-16 h-16 rounded-2xl bg-cyan-500/10 border border-cyan-500/20 text-cyan-400 flex items-center justify-center shadow-glow-cyan mb-1">
            <UploadCloud className="w-8 h-8" />
          </div>

          <h3 className="text-xl sm:text-2xl font-bold tracking-tight text-white">
            Drop batch images here
          </h3>

          <p className="text-xs sm:text-sm text-slate-400">
            or <span className="text-cyan-400 font-semibold underline underline-offset-4">browse files</span> from your computer
          </p>

          <div className="flex items-center gap-2 mt-2">
            <span className="text-[11px] font-mono px-2.5 py-1 rounded-full bg-white/5 border border-white/10 text-slate-300">
              JPG • PNG • WEBP
            </span>
            <span className="text-[11px] font-mono px-2.5 py-1 rounded-full bg-white/5 border border-white/10 text-slate-300">
              Up to 1,000 images per batch
            </span>
          </div>
        </div>
      </div>

      {/* Staged Images Preview Bar */}
      {stagedFiles.length > 0 && (
        <div className="glass-panel rounded-2xl p-4 border border-white/10 space-y-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <ImageIcon className="w-4 h-4 text-cyan-400" />
              <span className="text-xs font-bold text-white uppercase tracking-wider">
                Staged Images ({stagedFiles.length})
              </span>
            </div>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                className="text-xs text-cyan-400 hover:text-cyan-300 font-medium flex items-center gap-1 transition-colors"
              >
                <Plus className="w-3.5 h-3.5" />
                <span>Add More</span>
              </button>
              <button
                type="button"
                onClick={onClearAll}
                className="text-xs text-rose-400 hover:text-rose-300 font-medium flex items-center gap-1 transition-colors ml-2"
              >
                <Trash2 className="w-3.5 h-3.5" />
                <span>Clear All</span>
              </button>
            </div>
          </div>

          {/* Thumbnail list */}
          <div className="grid grid-cols-2 sm:grid-cols-4 md:grid-cols-6 lg:grid-cols-8 gap-3 max-h-80 overflow-y-auto pr-1">
            {stagedFiles.map((item) => (
              <div
                key={item.id}
                className="group relative rounded-xl overflow-hidden bg-black/40 border border-white/10 p-1.5 flex flex-col gap-1 hover:border-cyan-500/50 transition-all"
              >
                <div className="relative aspect-square rounded-lg overflow-hidden bg-slate-900 flex items-center justify-center">
                  <img
                    src={item.previewUrl}
                    alt={item.file.name}
                    className="w-full h-full object-cover"
                  />
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      onRemoveFile(item.id);
                    }}
                    className="absolute top-1 right-1 w-5 h-5 rounded-full bg-black/70 text-rose-400 hover:bg-rose-500 hover:text-white flex items-center justify-center transition-colors opacity-0 group-hover:opacity-100"
                  >
                    <X className="w-3 h-3" />
                  </button>
                </div>

                <div className="text-[10px] leading-tight truncate font-mono text-slate-300 px-0.5">
                  {item.file.name}
                </div>
                <div className="text-[9px] text-slate-400 px-0.5 flex justify-between">
                  <span>{(item.size / (1024 * 1024)).toFixed(1)}MB</span>
                  <span>{item.format}</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};
