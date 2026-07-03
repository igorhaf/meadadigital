'use client'
import { useResetWhen } from '@/lib/use-synced-form'

import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'

import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { uploadDocument } from '@/lib/supabase/knowledge'

const MAX_BYTES = 5 * 1024 * 1024 // 5MB — espelha o limite do backend (MAX_BYTES / yml)

/**
 * Modal de envio de PDF para a base de conhecimento (camada 5.13.e). Dois campos: título
 * (texto) e arquivo (PDF). O arquivo NÃO é controlado por react-hook-form — file inputs
 * não fazem bind limpo no RHF; uso um ref + estado local e valido na mão (tipo PDF, ≤5MB).
 *
 * O upload é SÍNCRONO no backend (extrai texto → chunka → embeda → persiste): pode levar
 * 20-40s. Durante a mutation, o botão mostra "Processando…" e os campos ficam travados —
 * o usuário não fecha o modal nem reenvia. Sucesso → invalida a lista e fecha.
 */
export function KnowledgeUploadDialog({
  open,
  onClose,
}: {
  open: boolean
  onClose: () => void
}) {
  const queryClient = useQueryClient()
  const fileRef = useRef<HTMLInputElement>(null)
  const [title, setTitle] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [validationError, setValidationError] = useState<string | null>(null)
  const [serverError, setServerError] = useState<string | null>(null)

  // Limpa o form sempre que abre (sem estado stale entre aberturas).
  useResetWhen(open, () => {
      setTitle('')
      setFile(null)
      setValidationError(null)
      setServerError(null)
      if (fileRef.current) fileRef.current.value = ''
  })

  const mutation = useMutation({
    mutationFn: () => uploadDocument(file!, title.trim()),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-knowledge'] })
      onClose()
    },
    onError: (err) => {
      console.error('uploadDocument failed:', err)
      // O backend manda reason no corpo (ex.: not_a_pdf, invalid_file_size,
      // ingestion_failed); o SDK joga como Error(reason). Mensagem amigável por caso.
      const reason = err instanceof Error ? err.message : ''
      setServerError(
        reason === 'not_a_pdf'
          ? 'O arquivo precisa ser um PDF.'
          : reason === 'invalid_file_size'
            ? 'Arquivo vazio ou maior que 5MB.'
            : reason === 'ingestion_failed'
              ? 'Não foi possível processar o PDF (texto ilegível ou protegido?).'
              : 'Erro ao enviar o documento. Tente novamente.',
      )
    },
  })

  function onPickFile(e: React.ChangeEvent<HTMLInputElement>) {
    setServerError(null)
    setValidationError(null)
    const picked = e.target.files?.[0] ?? null
    if (picked && picked.type !== 'application/pdf') {
      setValidationError('Selecione um arquivo PDF.')
      setFile(null)
      return
    }
    if (picked && picked.size > MAX_BYTES) {
      setValidationError('O PDF precisa ter no máximo 5MB.')
      setFile(null)
      return
    }
    setFile(picked)
  }

  function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    setServerError(null)
    if (!title.trim()) {
      setValidationError('Informe um título.')
      return
    }
    if (!file) {
      setValidationError('Selecione um PDF.')
      return
    }
    setValidationError(null)
    mutation.mutate()
  }

  // Bloqueia fechar enquanto envia (a request síncrona está em voo).
  function handleClose() {
    if (mutation.isPending) return
    onClose()
  }

  return (
    <Modal open={open} onClose={handleClose} title="Enviar documento">
      <form onSubmit={onSubmit} className="space-y-4">
        <div>
          <label htmlFor="doc-title" className="mb-1 block text-sm font-medium">
            Título
          </label>
          <input
            id="doc-title"
            type="text"
            placeholder="Manual de garantia"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            disabled={mutation.isPending}
            className="w-full rounded-md border border-border px-3 py-2 text-sm"
          />
        </div>

        <div>
          <label htmlFor="doc-file" className="mb-1 block text-sm font-medium">
            Arquivo (PDF, até 5MB)
          </label>
          <input
            id="doc-file"
            ref={fileRef}
            type="file"
            accept="application/pdf"
            onChange={onPickFile}
            disabled={mutation.isPending}
            className="w-full rounded-md border border-border px-3 py-2 text-sm file:mr-3 file:rounded-md file:border-0 file:bg-muted file:px-3 file:py-1 file:text-sm"
          />
        </div>

        {validationError && <p className="text-sm text-destructive">{validationError}</p>}
        {serverError && <p className="text-sm text-destructive">{serverError}</p>}

        {mutation.isPending && (
          <p className="text-sm text-muted-foreground">
            Processando… isso pode levar 20-40 segundos (extração e indexação do PDF).
          </p>
        )}

        <div className="flex justify-end gap-2 pt-2">
          <Button
            type="button"
            variant="outline"
            onClick={handleClose}
            disabled={mutation.isPending}
          >
            Cancelar
          </Button>
          <Button type="submit" disabled={mutation.isPending}>
            {mutation.isPending ? 'Processando…' : 'Enviar'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
