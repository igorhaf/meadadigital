'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'

import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { createFaq, updateFaq, type Faq } from '@/lib/supabase/faqs'

// question e answer obrigatórios (text NOT NULL no banco). Sem campos opcionais — mais
// simples que o form de service.
const faqSchema = z.object({
  question: z.string().min(1, 'Informe a pergunta'),
  answer: z.string().min(1, 'Informe a resposta'),
})

type FaqForm = z.infer<typeof faqSchema>

/**
 * Modal dual de FAQ (camada 5.5): cria ou edita conforme a prop `faq`.
 *  - faq ausente → modo CRIAÇÃO (chama createFaq com companyId).
 *  - faq presente → modo EDIÇÃO (pré-popula, chama updateFaq pelo id; companyId não é
 *    necessário no UPDATE — o RLS já garante que a FAQ é da empresa do tenant).
 *
 * `initialQuestion` (camada 5.18 #54) só vale no modo CRIAÇÃO (faq ausente): pré-popula o
 * campo Pergunta com o texto de uma sugestão da IA. Ignorado na edição (o faq manda).
 * Callers antigos não passam — backward compatible.
 *
 * O reset() num useEffect sincroniza o form quando o dialog abre OU o registro muda —
 * sem isso, o RHF manteria valores stale entre aberturas (ex.: abrir editar B logo após
 * fechar editar A mostraria os campos de A).
 */
export function CreateFaqDialog({
  open,
  onClose,
  companyId,
  faq,
  initialQuestion,
}: {
  open: boolean
  onClose: () => void
  companyId: string
  faq?: Faq
  initialQuestion?: string
}) {
  const queryClient = useQueryClient()
  const [serverError, setServerError] = useState<string | null>(null)
  const isEdit = faq != null

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<FaqForm>({ resolver: zodResolver(faqSchema) })

  // Sincroniza os campos quando abre (ou troca de registro): edição pré-popula, criação
  // limpa. Depende de open também para repreencher ao reabrir o mesmo registro.
  useEffect(() => {
    if (open) {
      // Edição: faq manda. Criação: usa initialQuestion (sugestão da IA) se houver, senão
      // limpa. answer começa vazio na criação (faq?.answer ?? '').
      reset({
        question: faq?.question ?? initialQuestion ?? '',
        answer: faq?.answer ?? '',
      })
      setServerError(null)
    }
  }, [open, faq, initialQuestion, reset])

  const mutation = useMutation({
    mutationFn: (values: FaqForm) =>
      isEdit
        ? updateFaq(faq!.id, values)
        : createFaq({ companyId, question: values.question, answer: values.answer }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-faqs'] })
      handleClose()
    },
    onError: (err) => {
      console.error(isEdit ? 'updateFaq failed:' : 'createFaq failed:', err)
      setServerError(
        isEdit
          ? 'Erro ao salvar alterações. Tente novamente.'
          : 'Erro ao criar FAQ. Tente novamente.',
      )
    },
  })

  function handleClose() {
    reset({ question: '', answer: '' })
    setServerError(null)
    onClose()
  }

  function onSubmit(values: FaqForm) {
    setServerError(null)
    mutation.mutate(values)
  }

  return (
    <Modal open={open} onClose={handleClose} title={isEdit ? 'Editar FAQ' : 'Nova FAQ'}>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div>
          <label htmlFor="question" className="mb-1 block text-sm font-medium">
            Pergunta
          </label>
          <input
            id="question"
            type="text"
            placeholder="Vocês atendem aos sábados?"
            className="w-full rounded-md border border-border px-3 py-2 text-sm"
            {...register('question')}
          />
          {errors.question && (
            <p className="mt-1 text-sm text-destructive">{errors.question.message}</p>
          )}
        </div>

        <div>
          <label htmlFor="answer" className="mb-1 block text-sm font-medium">
            Resposta
          </label>
          <textarea
            id="answer"
            rows={4}
            placeholder="Sim, das 9h às 13h."
            className="w-full rounded-md border border-border px-3 py-2 text-sm"
            {...register('answer')}
          />
          {errors.answer && (
            <p className="mt-1 text-sm text-destructive">{errors.answer.message}</p>
          )}
        </div>

        {serverError && <p className="text-sm text-destructive">{serverError}</p>}

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="outline" onClick={handleClose}>
            Cancelar
          </Button>
          <Button type="submit" disabled={isSubmitting || mutation.isPending}>
            {mutation.isPending
              ? isEdit
                ? 'Salvando…'
                : 'Criando…'
              : isEdit
                ? 'Salvar alterações'
                : 'Criar'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
