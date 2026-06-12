'use client';

import { ChangeEvent, DragEvent, FormEvent, useCallback, useEffect, useMemo, useState } from 'react';
import { api, AiDocumentImport } from '../utils/api';

interface AiDocumentsSectionProps {
  showToast: (message: string, type?: 'success' | 'error') => void;
}

const MAX_FILE_SIZE = 50 * 1024 * 1024;
const ACCEPTED_EXTENSIONS = ['.md', '.txt', '.pdf', '.docx'];

function formatBytes(value: number): string {
  if (!value) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB'];
  const index = Math.min(Math.floor(Math.log(value) / Math.log(1024)), units.length - 1);
  return `${(value / (1024 ** index)).toFixed(index === 0 ? 0 : 1)} ${units[index]}`;
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

function getExtension(fileName: string): string {
  const dotIndex = fileName.lastIndexOf('.');
  return dotIndex >= 0 ? fileName.slice(dotIndex).toLowerCase() : '';
}

function isRunning(item: AiDocumentImport): boolean {
  return item.status === 'queued' || item.status === 'processing';
}

function statusLabel(status: AiDocumentImport['status']): string {
  if (status === 'completed') return 'Hoàn tất';
  if (status === 'failed') return 'Lỗi';
  if (status === 'queued') return 'Đang chờ';
  if (status === 'processing') return 'Đang index';
  return 'Đã nhận';
}

function statusClass(status: AiDocumentImport['status']): string {
  if (status === 'completed') return 'bg-emerald-50 text-emerald-700';
  if (status === 'failed') return 'bg-red-50 text-red-700';
  if (status === 'queued') return 'bg-slate-100 text-slate-600';
  return 'bg-amber-50 text-amber-700';
}

export default function AiDocumentsSection({ showToast }: AiDocumentsSectionProps) {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [imports, setImports] = useState<AiDocumentImport[]>([]);
  const [uploading, setUploading] = useState(false);
  const [dragActive, setDragActive] = useState(false);
  const [lastMessage, setLastMessage] = useState('');

  const acceptedText = useMemo(() => ACCEPTED_EXTENSIONS.join(', '), []);
  const hasRunningImports = useMemo(() => imports.some(isRunning), [imports]);

  const refreshImports = useCallback(async () => {
    try {
      setImports(await api.getAiDocumentImports());
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Không thể tải lịch sử import AI.', 'error');
    }
  }, [showToast]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void refreshImports();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [refreshImports]);

  useEffect(() => {
    if (!hasRunningImports) return undefined;
    const timer = window.setInterval(() => {
      void refreshImports();
    }, 3000);
    return () => window.clearInterval(timer);
  }, [hasRunningImports, refreshImports]);

  function validateFile(file: File): string | null {
    const extension = getExtension(file.name);
    if (!ACCEPTED_EXTENSIONS.includes(extension)) {
      return `Chỉ hỗ trợ ${acceptedText}.`;
    }
    if (file.size > MAX_FILE_SIZE) {
      return 'File vượt quá giới hạn 50 MB của gateway/API.';
    }
    return null;
  }

  function chooseFile(file?: File) {
    if (!file) return;
    const error = validateFile(file);
    if (error) {
      showToast(error, 'error');
      return;
    }
    setSelectedFile(file);
    setLastMessage('');
  }

  function handleFileInput(event: ChangeEvent<HTMLInputElement>) {
    chooseFile(event.target.files?.[0]);
  }

  function handleDrop(event: DragEvent<HTMLLabelElement>) {
    event.preventDefault();
    setDragActive(false);
    chooseFile(event.dataTransfer.files?.[0]);
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!selectedFile) {
      showToast('Vui lòng chọn tài liệu để import.', 'error');
      return;
    }
    setUploading(true);
    try {
      const result = await api.uploadAiDocument(selectedFile);
      const message = result.message || 'Đã nhận tài liệu. AI đang cập nhật cơ sở dữ liệu nền.';
      setLastMessage(message);
      showToast(message);
      setSelectedFile(null);
      setImports((current) => [result.job, ...current.filter((item) => item.id !== result.job.id)]);
      void refreshImports();
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Không thể upload tài liệu AI.', 'error');
    } finally {
      setUploading(false);
    }
  }

  return (
    <div className="space-y-5">
      <section className="rounded-3xl bg-gradient-to-r from-[#102250] to-[#1f6f78] p-6 text-white shadow-xl shadow-slate-950/10">
        <div className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
          <div className="max-w-2xl">
            <p className="text-xs font-bold uppercase tracking-[0.18em] text-cyan-100">POST /api/ai/upload</p>
            <h2 className="mt-2 text-2xl font-black">Import tài liệu vào cơ sở dữ liệu AI</h2>
            <p className="mt-2 text-sm leading-6 text-cyan-50/80">File được gửi tới AI service, lưu vào thư mục handbook/uploads, tạo job import và cập nhật tiến trình khi cắt chunk, index DB, nạp lại RAG.</p>
          </div>
          <div className="rounded-2xl border border-white/15 bg-white/10 px-4 py-3">
            <p className="text-xs font-semibold text-cyan-50/70">Giới hạn upload</p>
            <p className="mt-1 text-sm font-bold">50 MB mỗi file</p>
          </div>
        </div>
      </section>

      <section className="grid gap-5 xl:grid-cols-[1fr_360px]">
        <form onSubmit={handleSubmit} className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <div className="flex items-start justify-between gap-4">
            <div>
              <h3 className="font-bold">Tài liệu mới</h3>
              <p className="mt-1 text-sm text-slate-500">Chọn file nội dung nội bộ để AI có thêm dữ liệu trả lời.</p>
            </div>
            <span className="rounded-full bg-cyan-50 px-3 py-1 text-xs font-bold text-cyan-700">RAG DB</span>
          </div>

          <label
            onDragOver={(event) => { event.preventDefault(); setDragActive(true); }}
            onDragLeave={() => setDragActive(false)}
            onDrop={handleDrop}
            className={`mt-5 flex min-h-56 cursor-pointer flex-col items-center justify-center rounded-2xl border-2 border-dashed px-6 py-8 text-center transition ${
              dragActive ? 'border-cyan-500 bg-cyan-50' : 'border-slate-300 bg-slate-50 hover:border-cyan-400 hover:bg-cyan-50/60'
            }`}
          >
            <input type="file" accept={ACCEPTED_EXTENSIONS.join(',')} onChange={handleFileInput} className="sr-only" />
            <span className="grid h-14 w-14 place-items-center rounded-2xl bg-white text-2xl font-black text-cyan-700 shadow-sm">↑</span>
            <span className="mt-4 block text-base font-bold text-slate-900">{selectedFile ? selectedFile.name : 'Kéo file vào đây hoặc bấm để chọn'}</span>
            <span className="mt-2 text-sm text-slate-500">{selectedFile ? formatBytes(selectedFile.size) : acceptedText}</span>
          </label>

          {lastMessage && (
            <div className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-semibold text-emerald-800">{lastMessage}</div>
          )}

          <div className="mt-5 flex flex-col gap-3 border-t border-slate-200 pt-5 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-xs leading-5 text-slate-500">Sau khi upload, backend index tài liệu ở background. Câu trả lời AI sẽ dùng dữ liệu mới khi pipeline nạp lại xong.</p>
            <div className="flex gap-2">
              <button type="button" onClick={() => void refreshImports()} className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-bold text-slate-700 hover:bg-slate-50">Làm mới</button>
              <button disabled={!selectedFile || uploading} className="rounded-xl bg-cyan-700 px-5 py-2.5 text-sm font-bold text-white hover:bg-cyan-800 disabled:opacity-45">
                {uploading ? 'Đang import...' : 'Import vào AI'}
              </button>
            </div>
          </div>
        </form>

        <aside className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <p className="text-xs font-bold uppercase tracking-wider text-slate-400">Pipeline</p>
          <div className="mt-4 space-y-3 text-sm">
            {[
              ['1', 'Lưu file', 'data/handbook/uploads'],
              ['2', 'Cắt chunk', '01_prepare_data.py'],
              ['3', 'Index DB', '02_index_data.py'],
              ['4', 'Nạp lại RAG', 'AsyncQueryPipeline'],
            ].map(([step, title, note]) => (
              <div key={step} className="flex gap-3 rounded-xl bg-slate-50 p-3">
                <span className="grid h-7 w-7 shrink-0 place-items-center rounded-lg bg-cyan-100 text-xs font-black text-cyan-700">{step}</span>
                <span><span className="block font-bold text-slate-800">{title}</span><span className="mt-0.5 block text-xs text-slate-500">{note}</span></span>
              </div>
            ))}
          </div>
        </aside>
      </section>

      <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="flex flex-col gap-3 border-b border-slate-200 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h3 className="font-bold">Lịch sử và kết quả import</h3>
            <p className="mt-0.5 text-xs text-slate-500">Dữ liệu lấy từ AI service. Job đang chạy sẽ tự cập nhật mỗi 3 giây.</p>
          </div>
          {hasRunningImports && <span className="w-fit rounded-full bg-amber-50 px-3 py-1 text-xs font-bold text-amber-700">Đang theo dõi tiến trình</span>}
        </div>
        <div className="divide-y divide-slate-100">
          {imports.length === 0 ? (
            <div className="px-5 py-10 text-center text-sm text-slate-500">Chưa có tài liệu nào được import vào AI.</div>
          ) : imports.map((item) => (
            <div key={item.id} className="px-5 py-4">
              <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="truncate font-bold text-slate-900">{item.fileName}</p>
                    <span className={`rounded-full px-3 py-1 text-xs font-bold ${statusClass(item.status)}`}>{statusLabel(item.status)}</span>
                  </div>
                  <p className="mt-1 text-xs text-slate-500">
                    {formatBytes(item.fileSize)} · nhận lúc {formatDate(item.importedAt)}
                    {item.completedAt ? ` · xong lúc ${formatDate(item.completedAt)}` : ''}
                  </p>
                  {item.savedPath && <p className="mt-1 break-all text-xs text-slate-400">{item.savedPath}</p>}
                </div>
                <div className="text-left md:text-right">
                  <p className="text-sm font-black text-slate-900">{item.progress ?? 0}%</p>
                  <p className="mt-0.5 text-xs text-slate-500">{item.chunks != null ? `${item.chunks} chunks` : item.step}</p>
                </div>
              </div>

              <div className="mt-3 h-2 overflow-hidden rounded-full bg-slate-100">
                <div
                  className={`h-full rounded-full transition-all ${item.status === 'failed' ? 'bg-red-500' : item.status === 'completed' ? 'bg-emerald-500' : 'bg-cyan-600'}`}
                  style={{ width: `${Math.min(Math.max(item.progress ?? 0, 0), 100)}%` }}
                />
              </div>
              <p className={`mt-3 text-sm ${item.status === 'failed' ? 'font-semibold text-red-700' : 'text-slate-600'}`}>{item.error || item.message}</p>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
